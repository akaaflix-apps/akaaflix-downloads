# Installing Akaa Flix on a Mac

**Requirements:** macOS 11 Big Sur or later, on any Intel or Apple Silicon Mac (M1, M2, M3, M4, M5 …).
The single Universal app runs natively on both — no Rosetta.

## 1. Download

From the GitHub Actions run (artifact **AkaaFlix-4.0.7-Universal**) or from your release page:

| File | What it is |
|---|---|
| `AkaaFlix-4.0.7-Universal.dmg` | Installer disk image (recommended) |
| `AkaaFlix-4.0.7-Universal-app.zip` | The same app, zipped |
| `*.sha256` | Checksum to verify the download: `shasum -a 256 AkaaFlix-4.0.7-Universal.dmg` |

## 2. Install

1. Double-click `AkaaFlix-4.0.7-Universal.dmg`.
2. Drag **Akaa Flix** onto the **Applications** shortcut.
3. Eject the disk image.

## 3. First launch

### Builds signed with Developer ID and notarized
Open **Akaa Flix** from Applications — macOS verifies it automatically.

### Builds without a Developer ID (ad-hoc signed, not notarized)
The CI builds in this repository are ad-hoc signed unless Developer ID secrets are configured
(see below), so Gatekeeper asks for confirmation the first time:

- **macOS 15 Sequoia / macOS 26 Tahoe:** open the app once, click **Done**, then go to
  **System Settings → Privacy & Security**, scroll to *“Akaa Flix” was blocked…* and click
  **Open Anyway**, then confirm.
- **macOS 11 – 14:** Control-click (right-click) **Akaa Flix** in Applications → **Open** → **Open**.
- Alternative (Terminal, any version):

```bash
xattr -dr com.apple.quarantine "/Applications/Akaa Flix.app"
```

## 4. Add your playlist

The first screen is **إضافة قائمة جديدة / Add New Playlist**. Enter the four Xtream Codes fields
from your provider (playlist name, username, password, server URL), press **اختبار الاتصال / Test
Connection**, then **إضافة القائمة / Add Playlist**. You can also scan a QR code (QR button) that
contains an Xtream `get.php` link.

Akaa Flix ships no channels or content. Only connect to services you are authorised to use.

## Data locations

| Data | Location |
|---|---|
| Database, settings, image cache | `~/Library/Application Support/Akaa Flix/` |
| Playlist passwords | login Keychain (item “Akaa Flix playlist”) |
| Downloads | `~/Movies/Akaa Flix/` |
| Screenshots | `~/Pictures/Akaa Flix/` |

## Uninstall

Quit the app, move `/Applications/Akaa Flix.app` to the Bin, and optionally delete
`~/Library/Application Support/Akaa Flix/` and the Keychain items named “Akaa Flix playlist”.

## Signing and notarization (for the publisher)

To produce a Developer ID signed **and notarized** DMG, add these repository secrets and re-run the
workflow `.github/workflows/macos-app.yml`:

| Secret | Value |
|---|---|
| `MACOS_CERT_P12_BASE64` | Base64 of your *Developer ID Application* certificate + key (.p12) |
| `MACOS_CERT_PASSWORD` | Password of the .p12 |
| `APPLE_ID` | Apple ID used for notarization |
| `APPLE_TEAM_ID` | 10-character Team ID |
| `APPLE_APP_PASSWORD` | App-specific password for that Apple ID |

Locally: copy `macos/Config/Signing.example.xcconfig` to `macos/Config/Signing.xcconfig`, fill in
your identity/team, run `macos/scripts/build.sh Release`, `macos/scripts/make-dmg.sh …` with
`AKAAFLIX_SIGN_IDENTITY` set, then `macos/scripts/notarize.sh <dmg>`. A build is only notarized
when `notarize.sh` succeeds (it staples and validates the ticket).
