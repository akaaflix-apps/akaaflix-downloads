# Performance

Performance has priority over effects, without dropping the visual identity: every effect is drawn with
cheap, hardware-accelerated primitives and can be scaled down per device.

## Startup path

- No splash screen and no work on the critical path besides reading settings (one small DataStore JSON
  value, normally already loaded by `Application.onCreate`).
- App list from a **text cache** (`apps_cache_v1.tsv`) on cold start, then a background refresh and diff.
- Icons from a **PNG disk cache** keyed by package version (decoding a small PNG is much cheaper than opening
  every APK's resources), then a byte-bounded memory LRU.
- Home page is created once and only hidden when other pages open, so returning Home is instant and focus
  is preserved; other pages are created on demand and destroyed when left.
- Native Views instead of Compose (smaller runtime, faster first frame on low-end TV boxes).
- R8 full minification and resource shrinking, no native libraries. The approved wallpaper collection adds
  ~4.3 MB of WebP (native 1672×941, quality 90) – images are decoded once at screen size, so APK size does not
  change memory use.

## Rendering

- One custom drawable (`GlassDrawable`) renders cards, chips, panels and buttons: gradients cached per size
  and palette, focus glow drawn as a few rounded-rectangle strokes **inside** the view bounds (no blur, no
  software layers, no overdraw outside the view).
- Focus zoom uses `ViewPropertyAnimator` (render-thread animation). The focused card is drawn last instead of
  being re-ordered or elevated.
- Lightweight custom `ViewGroup` for app cards (no nested layouts).
- Grids are RecyclerViews with stable ids and DiffUtil; paged grids bind only one page (≤ ~30 cards).
- Wallpaper decoded once at screen size off the main thread (power-of-two sub-sampling + cover scaling):
  an 8K image never allocates more than ~2× the screen; imported images are stored as optimised ≤4K JPEGs.
  4K bundled wallpapers are 55–115 KB WebP files.
- Animated Neon Pulse: 7 polylines (57 points) stroked three times per frame at ~30 fps, preallocated
  paint/path, no bitmaps or blur filters; it stops completely when the launcher is not visible and is replaced by
  its static frame in lightweight mode. Video wallpapers pause in `onStop`.
- Neon focus: drawn by the card's own drawable inside its bounds (glow rings within an 8 dp margin, border,
  inner light), cross-faded by a ~150 ms animator only while focus changes; the halo is disabled on entry-tier
  devices (border and inner light remain).

## No background work

- No long-running services, no WorkManager, no alarms. Clock updates come from the system `TIME_TICK`
  broadcast; Wi-Fi from callbacks; all are registered only while the launcher is visible.
- Weather: refreshed while Home is visible (≥ 30 min apart) and by one JobScheduler job (~hourly, network
  required, battery not low, only when a weather city is set). Location is read once on request (and at most
  every 12 h while visible for an auto-detected city) – never continuously.
- Wallpaper rotation runs only while visible (an overdue rotation happens on return).
- Network only for weather and update checks; startup never waits for the network (cached weather is shown).

## Device tiers and lightweight mode

`DevicePerformance` classifies the device from total RAM, `isLowRamDevice` and cores:

| Tier | RAM | Auto mode | Effects |
|---|---|---|---|
| LOW | ≤ 1.5 GB or low-RAM device | **Lightweight** | no animations, no glow, reduced zoom, no video/animated wallpaper, RGB_565 wallpaper ≤ 1920 px, small caches |
| MEDIUM | ≤ 3 GB | Balanced | shorter animations, video allowed, wallpaper ≤ 2560 px |
| HIGH | ≤ 6 GB | High quality | all effects, wallpaper ≤ 3840 px |
| ULTRA | > 6 GB | High quality | all effects, largest caches |

Memory caches scale with the per-app heap (`memoryClass`): icons 3–32 MB, thumbnails 3–12 MB. The user can
force *High quality*, *Balanced* or *Lightweight* in Settings → Power & performance. `onTrimMemory` trims icon
and thumbnail caches and, on lightweight devices, releases the wallpaper bitmap while another app is in front.

## Reliability

- Crash guard: three crashes within three minutes start the next launch in safe mode (lightweight rendering,
  default wallpaper, system font).
- Debug builds enable StrictMode (network on main thread, leaked closeables, activity leaks).
- All system intents go through `SafeIntents` (never crash on devices missing a settings screen).

## Not yet measured

Cold-start time, frame timing and memory have **not** been measured on hardware (no device in the build
environment). Recommended: `adb shell am start -W` for start time, `adb shell dumpsys gfxinfo
com.akaaflix.launcher` for frame stats and `adb shell dumpsys meminfo com.akaaflix.launcher` on a 1 GB box
in lightweight mode. A Baseline Profile (Macrobenchmark) should be generated once a test device is available.
