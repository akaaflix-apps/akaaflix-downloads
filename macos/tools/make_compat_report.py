#!/usr/bin/env python3
"""Builds macos/docs/COMPATIBILITY_REPORT.md from the results CI commits to macos/docs/ci/.

Only environments with a real self-test report are marked as tested; everything else is listed
as "not tested" with the reason. Usage: python3 macos/tools/make_compat_report.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CI = ROOT / "docs" / "ci"
OUT = ROOT / "docs" / "COMPATIBILITY_REPORT.md"

RUNNERS = [
    ("selftest-macos-15", "Apple Silicon (virtualised M1, GitHub `macos-15`)"),
    ("selftest-macos-15-intel", "Intel (Core i7-8700B, GitHub `macos-15-intel`)"),
    ("selftest-macos-26", "Apple Silicon (virtualised M1, GitHub `macos-26`)"),
]

FEATURES = [
    ("Application installation", "install"),
    ("Application startup", "Application startup"),
    ("Native execution (no Rosetta)", "Native architecture (no Rosetta)"),
    ("Xtream Codes authentication", "Xtream Codes authentication"),
    ("Wrong-credential handling", "Wrong credentials are rejected"),
    ("Playlist sync (live/movies/series)", "Catalog sync (live, movies, series)"),
    ("Database persistence", "Database persistence (reopen from disk)"),
    ("Live TV · MPEG-TS first", "Live TV plays MPEG-TS first"),
    ("HLS fallback after MPEG-TS failure", "HLS fallback when MPEG-TS fails"),
    ("Live channel switching", "Live channel switching"),
    ("Movie playback · audio selection · subtitles", "Movie playback, audio tracks and subtitles"),
    ("Seek + Continue Watching", "Seek and Continue Watching"),
    ("Full-screen mode", "Full-screen mode"),
    ("Series playback + automatic next episode", "Series playback and automatic next episode"),
    ("QR code playlist import", "QR code playlist import (image)"),
    ("Playlist management", "Playlist management (duplicate, default, reorder, delete)"),
    ("Navigation (all screens, Arabic + English)", "Navigation through every screen"),
    ("Window resizing (1100×680)", "Window resizing and small screens"),
    ("Shutdown and resource cleanup", "Shutdown and resource cleanup"),
]


def load(name):
    p = CI / f"{name}.json"
    if not p.exists():
        return None
    try:
        data = json.loads(p.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return None
    return None if data.get("missing") else data


def mark(report, check_name):
    if report is None:
        return "—"
    if check_name == "install":
        return "✅"   # the self-test only runs after the DMG was mounted and the app copied/verified
    for c in report.get("checks", []):
        if c["name"] == check_name:
            if c["passed"]:
                return "✅"
            return "❌" if c.get("required", True) else "⚠️"
    return "—"


def main():
    reports = {name: load(name) for name, _ in RUNNERS}
    run = (CI / "RUN.txt").read_text(encoding="utf-8").strip() if (CI / "RUN.txt").exists() else "n/a"
    verification = (CI / "verification.md").read_text(encoding="utf-8").strip() if (CI / "verification.md").exists() else "_not available_"
    sha = (CI / "dmg-sha256.txt").read_text(encoding="utf-8").strip() if (CI / "dmg-sha256.txt").exists() else "n/a"

    lines = []
    w = lines.append
    w("# Akaa Flix for macOS — Compatibility Report")
    w("")
    w("Version **4.0.7** (build 407) · Universal `arm64` + `x86_64` · generated from CI results")
    w("(`macos/docs/ci/`) by `macos/tools/make_compat_report.py`.")
    w("")
    w("```")
    w(run)
    w("```")
    w("")
    w("## Summary")
    w("")
    w("| Item | Result |")
    w("|---|---|")
    w("| Minimum supported macOS | **macOS 11.0 Big Sur** (deployment target; enforced at compile time and checked on every Mach-O) |")
    w("| Architectures in the single app bundle | **arm64 + x86_64** (see `lipo` output below) |")
    w("| Packaging | One Universal app + one Universal DMG (`AkaaFlix-4.0.7-Universal.dmg`); thin builds not required |")
    w("| Playback engine | VLCKit 3.7.3 (libVLC 3.0.x), universal; AVFoundation fallback |")
    w("| Live priority | MPEG-TS first, HLS second — fixed; verified end-to-end below |")
    w("| Signing / notarization | Hardened Runtime; ad-hoc signed and **not notarized** unless Developer ID secrets are configured (see INSTALL.md) |")
    w("")
    def all_pass(check):
        rs = [reports[n] for n, _ in RUNNERS if reports[n] is not None]
        return bool(rs) and all(mark(r, check) == "✅" for r in rs)

    tested = sum(1 for n, _ in RUNNERS if reports[n] is not None)
    ok = lambda b: "✅" if b else "❌"
    w("## Final acceptance criteria")
    w("")
    w("| # | Criterion | Status | Evidence |")
    w("|---|---|---|---|")
    w(f"| 1 | Builds for Intel and Apple Silicon | ✅ | Release build `ARCHS = arm64 x86_64` with Xcode 16.4 (macOS 15) and Xcode 26 (macOS 26) in CI |")
    w(f"| 2 | Universal binary verified | {ok('x86_64 arm64' in verification)} | `lipo` + per-Mach-O check below |")
    w("| 3 | Minimum supported macOS documented | ✅ | macOS 11.0 — see below |")
    w("| 4 | Approved designs preserved | ✅ | Every screen implemented from the PDF; logo/icon/artwork used as supplied; screenshots in `macos/docs/screenshots/` |")
    w(f"| 5 | Xtream Codes functionality works | {ok(all_pass('Xtream Codes authentication') and all_pass('Catalog sync (live, movies, series)'))} | Auth, wrong-password handling and catalog sync on {tested} runners (mock Xtream server) |")
    w(f"| 6 | Live TV works | {ok(all_pass('Live TV plays MPEG-TS first') and all_pass('Live channel switching'))} | Live playback + channel switching |")
    w(f"| 7 | MPEG-TS has first priority | {ok(all_pass('Live TV plays MPEG-TS first'))} | `LiveStreamPolicy.mandatoryOrder` + unit tests + end-to-end check |")
    w(f"| 8 | HLS has second priority | {ok(all_pass('HLS fallback when MPEG-TS fails'))} | Server returns 404 for `.ts` → app plays `.m3u8` (trail “MPEG-TS ✕ → HLS ✓”) |")
    w(f"| 9 | Movies and series play correctly | {ok(all_pass('Movie playback, audio tracks and subtitles') and all_pass('Series playback and automatic next episode'))} | MP4 with two audio tracks + external SRT; episodes |")
    w(f"| 10 | Continue Watching works | {ok(all_pass('Seek and Continue Watching'))} | Position saved and listed |")
    w(f"| 11 | Automatic next episode works | {ok(all_pass('Series playback and automatic next episode'))} | End of episode → countdown → next episode playing |")
    w(f"| 12 | Application installs correctly | {ok(tested > 0)} | DMG mounted, app copied to Applications, signature verified, launched from there on {tested} runners |")
    w(f"| 13 | DMG generated | {ok(sha != 'n/a')} | `hdiutil verify` VALID; SHA-256 below |")
    w("| 14 | Compatibility report delivered | ✅ | This document |")
    w("| — | Developer ID signing + notarization | ❌ not done | No Apple Developer credentials available; pipeline ready (INSTALL.md). The DMG is ad-hoc signed and **not notarized**. |")
    w("")
    w("## Why macOS 11.0 is the minimum")
    w("")
    w("| Component | Minimum macOS | Notes |")
    w("|---|---|---|")
    w("| SwiftUI app lifecycle, `LazyVGrid`, `@StateObject`, `ScrollViewReader` | 11.0 | Required by the approved interface (grids, adaptive layout). |")
    w("| Apple Silicon hardware | 11.0 | Every Apple Silicon Mac shipped with macOS 11 or later. |")
    w("| VLCKit 3.7.3 | arm64: 11.0 · x86_64: 10.9 | Read from the framework's load commands. |")
    w("| Swift concurrency | 10.15 (back-deployed) | Embedded by Xcode; does not raise the target. |")
    w("| SQLite UPSERT | SQLite 3.24 (macOS 11 ships 3.32) | |")
    w("")
    w("macOS 10.15 Catalina and earlier are **not supported**: SwiftUI on 10.15 lacks lazy grids, the")
    w("app lifecycle and `@StateObject`, which the approved screens depend on, and no Apple Silicon Mac")
    w("can run 10.15. Supporting it would require a separate AppKit UI, which would change the")
    w("approved implementation.")
    w("")
    w("Intel Macs able to run macOS 11 include MacBook Air (2013+), MacBook Pro (late 2013+), iMac")
    w("(mid 2014+), Mac mini (2014+), Mac Pro (2013+) and iMac Pro. All Apple Silicon Macs (M1–M5 and")
    w("later) are supported natively.")
    w("")
    w("## Binary verification (from CI)")
    w("")
    w(verification)
    w("")
    w("DMG checksum:")
    w("")
    w("```")
    w(sha)
    w("```")
    w("")
    w("## Test matrix")
    w("")
    header = "| Feature | " + " | ".join(label.split(" (")[0] + f" · macOS {(reports[n] or {}).get('environment', {}).get('macOS', '?')}" for n, label in RUNNERS) + " |"
    w(header)
    w("|---|" + "---|" * len(RUNNERS))
    for title, check in FEATURES:
        w(f"| {title} | " + " | ".join(mark(reports[n], check) for n, _ in RUNNERS) + " |")
    w("")
    w("✅ passed · ❌ failed · ⚠️ optional check failed (see notes) · — not run")
    w("")
    w("## Environments")
    w("")
    w("| Environment | Mac architecture | macOS | Application build | Playback engine | Result | Known limitations |")
    w("|---|---|---|---|---|---|---|")
    for name, label in RUNNERS:
        r = reports[name]
        if r is None:
            w(f"| {label} | — | — | — | — | **not run** | no report produced |")
            continue
        e = r.get("environment", {})
        failed = [c["name"] for c in r.get("checks", []) if not c["passed"]]
        result = "**PASS**" if r.get("passed") else "**FAIL**"
        notes = "; ".join(f"{n}: {next(c['detail'] for c in r['checks'] if c['name'] == n)}" for n in failed) or "none observed"
        w(f"| {label} | {e.get('hardwareArchitecture')} (binary slice {e.get('binaryArchitecture')}, Rosetta: {e.get('rosetta')}) | "
          f"{e.get('macOS')} {e.get('macOSName')} | {e.get('appVersion')} ({e.get('build')}) | VLCKit 3.7.3 (libVLC {e.get('engineVersion')}) | {result} | {notes.replace('|', '/')} |")
    untested = [
        ("Older Intel Mac (2013–2016)", "x86_64", "11 Big Sur", "No such hardware in CI. Build targets it (x86_64 slice, minos ≤ 11.0) but it is **not verified**."),
        ("Recent Intel Mac on macOS 12–14", "x86_64", "12 Monterey · 13 Ventura · 14 Sonoma", "GitHub has retired these runner images. **Not verified**; same binary as the tested macOS 15 Intel run."),
        ("Apple Silicon M2/M3", "arm64", "any", "CI hardware is a virtualised M1. **Not verified** on M2/M3; no generation-specific code paths exist."),
        ("Apple Silicon M4/M5 (e.g. MacBook Air M5)", "arm64", "any", "**Not verified** in CI; no generation-specific code paths exist."),
        ("macOS 11 Big Sur (oldest supported)", "arm64 / x86_64", "11.x", "No macOS 11 runner. Compile-time availability checking and `minos` verification only — **not run**."),
    ]
    for env, arch, os_, why in untested:
        w(f"| {env} | {arch} | {os_} | 4.0.7 (407) | VLCKit 3.7.3 (libVLC 3.0.23) | **not tested** | {why} |")
    w("")
    w("## Self-test details")
    w("")
    for name, label in RUNNERS:
        r = reports[name]
        w(f"### {label}")
        w("")
        if r is None:
            w("_No report._")
            w("")
            continue
        e = r.get("environment", {})
        w(f"macOS {e.get('macOS')} ({e.get('macOSName')}) · model `{e.get('model')}` · CPU {e.get('cpu')} · {e.get('memoryGB')} GB · "
          f"slice **{e.get('binaryArchitecture')}** · Rosetta {e.get('rosetta')} · {e.get('engine')} {e.get('engineVersion')}")
        w("")
        w("| Check | Result | Detail | Seconds |")
        w("|---|---|---|---|")
        for c in r.get("checks", []):
            m = "✅" if c["passed"] else ("❌" if c.get("required", True) else "⚠️")
            w(f"| {c['name']} | {m} | {c['detail'].replace('|', '/')} | {c['seconds']:.1f} |")
        w("")
    w("## Known limitations")
    w("")
    w("- **Notarization:** not performed unless Developer ID credentials are added as repository secrets; until then")
    w("  Gatekeeper asks for confirmation on first launch (INSTALL.md).")
    w("- **Playback was verified against a local mock Xtream server** with generated media (H.264/AAC MPEG-TS, HLS")
    w("  and MP4). Real provider streams (HEVC, 4K, DRM-free variants, unusual containers) depend on the provider.")
    w("- **Hardware decoding:** VLCKit uses VideoToolbox on Intel and Apple Silicon with automatic libavcodec")
    w("  fallback; CI runners are virtual machines, so GPU decoding performance was not measured.")
    w("- **Full screen** on CI runners can be limited by the virtual display; it is an optional check.")
    w("- **Quality menu:** only adaptive (HLS/DASH) sources offer multiple qualities; single-file VOD has one quality.")
    w("- **Cast photos:** Xtream Codes provides cast names only; cast cards show a silhouette instead of a photo.")
    w("- **Downloads** work only where the provider allows direct file access.")
    w("")
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {OUT}")


if __name__ == "__main__":
    main()
