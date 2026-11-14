# Design specification (from the approved references)

Source: `Neon_Arabic_App_Launcher_Interface.pdf` – 8 images, copies in `docs/reference/`:

| # | File | Content |
|---|---|---|
| 1 | `01_all_apps.jpg` | All apps page |
| 2 | `02_app_icon.jpg` | App icon (Anubis, pyramids, Akaa Flix Launcher logo) |
| 3 | `03_home.jpg` | Home screen |
| 4 | `04_settings.jpg` | Settings page |
| 5 | `05_default_wallpaper.jpg` | Default wallpaper (1671×941) |
| 6 | `06_logo.jpg` | Logo on white (2172×724) |
| 7 | `07_wallpapers.jpg` | Wallpapers page |
| 8 | `08_customization.jpg` | Customization page |

## Canvas and scale

References are 1671×941 px (16:9). Android TV renders 720p, 1080p and 4K UIs on a **960×540 dp** canvas, so
all measurements were converted with **dp = px × 0.5745**. Phones/tablets use the same dp sizes with adaptive
grids; portrait turns the sidebar into a bottom bar.

| Element | Reference (px) | Implementation (dp) |
|---|---|---|
| Sidebar width / item height | 165 / ~90 | 100 / ~52 |
| Brand pill | 217×78 | wrap × 48 |
| Clock text | 36 px | 21 sp |
| Date pill | 495×72 | wrap × 44 |
| Home logo | 475×125 | 282×82 |
| Home grid top / card | 343 / 228×165 | 188 / (width/6) × 0.74 incl. 6 dp glow margin |
| All apps card | 190×115, 7 columns | auto columns (min 112 dp), aspect 0.64 |
| Chips / search | height 56 | 40 (incl. glow margin) |
| Settings tile | 260×185, 4×3 | grid 4×3 weights |
| Right settings panel | 347 | 204 |
| Wallpaper thumbnail | 238×147, 4 columns | auto (min 134 dp), aspect 0.62 |
| Customization preview | 646×346 | 16:9 within a 196 dp row |

## Palette

| Token | Value | Use |
|---|---|---|
| Background | `#07020F` | window, behind wallpaper |
| Purple accent | `#A855F7` | selection, toggles, focus glow |
| Deep violet | `#6D28D9` | gradients |
| Neon | `#B44CFF` | logo mark |
| Blue | `#3D7BFF` | secondary accent / theme |
| Gold | `#FFC94A` | crown, resolution badge, weather sun/moon |
| Silver text | `#E8E6F0` / white | text |
| Glass | panel tint `#2A1452` at ~70% → darker bottom, 1 dp light-violet border | cards, panels |

Themes (Customization → Colors): Akaa Purple (default), Royal Blue, Pharaoh Gold, Silver Night, plus 8 accent
swatches. All drawables read the palette at draw time.

## Typography

Tajawal (default – closest to the reference's geometric Latin and bold Arabic), Cairo, or the system font.
Bundled as static Regular/Bold TTFs (OFL). Title 28 sp bold, subtitle 11–12 sp, card label 13 sp,
sidebar label ≤ 12 sp (auto-size).

## Components

- **Glass card / panel / chip** (`GlassDrawable`): translucent vertical gradient, top sheen, 1 dp border; cards add a
  bottom under-glow in the app's dominant colour (Netflix red, YouTube red, Disney blue… as in the references);
  focus = 2 dp near-white border + 4 neon rings + zoom.
- **Sidebar** (`SidebarDrawable`): curved convex edge with neon stroke; selected item pill attached to the screen edge.
- **Top bar**: brand pill (avatar on Home, play mark elsewhere, gold crown), centred page icon + title + subtitle,
  clock, Wi-Fi/Ethernet, Bluetooth, gear button, date pill (Gregorian | Hijri, Arabic + English lines).
- **Header art**: the pyramid + golden ring from the All apps reference, feathered, shown on secondary pages.
- **Toggle** (`NeonSwitch`): purple track, white knob (knob on the right when on, in both languages, as drawn).
- **Pagination**: circular arrows + dots.

## Arabic RTL decisions (please confirm)

The approved Arabic screens keep the **sidebar on the left** and the clock/date on the right, and the app grids
start at the **top-left** (first focused item next to the sidebar). To stay faithful:

- The screen frame (sidebar left, status top-right) and grids/tile matrices keep this structure in both languages.
- Text alignment, settings rows (label right, control left), chip rows (e.g. "All" on the right of the wallpaper
  categories), dialogs and the page title row (icon right of the Arabic title) follow the language direction.

If you prefer a fully mirrored Arabic layout (sidebar on the right), it can be enabled, but it changes the approved
composition, so it was not done without approval.

## Items that differ from, or extend, the references (please review)

1. **Real data instead of mock values**: the Hijri date shows the true Umm al-Qura date (the mock showed
   "27 Jumada Al-Awwal" for 8 Nov 2026; Umm al-Qura gives 28 – a ±2-day adjustment setting is provided).
   Version shows the real version (1.0.0), storage/RAM/device cards show real values, the weather widget is
   hidden until the user sets a city.
2. **Favorites page**: not drawn in the references; built with the same components (grid + recently used row).
3. **Settings tiles**: the 12 reference tiles are kept. Required sections without their own tile were placed
   logically: General → *Interface settings*; Performance → *Power management* (renamed "Power & performance" in
   English, subtitle "Performance, sleep mode"); Backup & restore + updates → *About*; Parental controls →
   *Profiles* (single launcher profile with name/avatar + PIN locks; Android multi-user management is not
   available to apps).
4. **"Cursor (mouse)" tile**: focus-indicator style, focus zoom, page transition and an in-launcher mouse pointer
   (Android 7.0+). A launcher cannot change the system-wide pointer.
5. **Wallpaper gallery content**: the PDF contains one full-resolution wallpaper (the default). The gallery adds
   nine original 4K renders made for this launcher (galaxy, moonrise, aurora, dusk lake, desert pyramids, neon
   waves, neon pyramid tunnel, purple silk) and two lightweight animated wallpapers (Neon Pulse on the default
   artwork, Starfield Drift). The photographic thumbnails in the reference (Cappadocia balloons, mosque, car…)
   are not reproduced because they are not available as licensed assets. An *Add from device* tile was added.
6. **Default wallpaper resolution**: the supplied artwork is 1671×941; it is stored upscaled (Lanczos) to
   1920×1080 and labelled FHD. A higher-resolution original would make it sharp on 4K screens.
7. **Home remote-control decoration**: cropped from the Home reference and feathered so it blends on any
   wallpaper; it is a shortcut to remote-control settings (can be hidden).
8. **Chips "Entertainment / Tools"** in All apps are filters only; there are no Games or System categories.
9. **Icon style swatches** map to: original, rounded square, circle (white plate for legacy icons), square.
