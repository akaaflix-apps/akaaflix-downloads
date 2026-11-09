#!/usr/bin/env bash
# Submits a Developer ID signed DMG to Apple's notary service and staples the ticket.
# Requires: APPLE_ID, APPLE_TEAM_ID, APPLE_APP_PASSWORD (app-specific password).
# Never report a build as notarized unless this script exits 0.
set -euo pipefail
DMG="${1:?usage: notarize.sh <dmg>}"
: "${APPLE_ID:?APPLE_ID not set}" "${APPLE_TEAM_ID:?APPLE_TEAM_ID not set}" "${APPLE_APP_PASSWORD:?APPLE_APP_PASSWORD not set}"
xcrun notarytool submit "$DMG" --apple-id "$APPLE_ID" --team-id "$APPLE_TEAM_ID" --password "$APPLE_APP_PASSWORD" --wait --timeout 30m
xcrun stapler staple "$DMG"
xcrun stapler validate "$DMG"
spctl --assess --type open --context context:primary-signature -v "$DMG"
echo "Notarized and stapled: $DMG"
