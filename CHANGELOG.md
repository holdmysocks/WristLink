# Changelog

## 0.8.0 — 2026-11-16

- Add Reply to native Messages notifications and their in-app details, using Bluetooth MAP through the existing iPhone pairing.
- Automatically open a draft for one uniquely matching message based on text, time, and valid sender identity. Ambiguous or unsupported entries retain a manual sender picker.
- Show names from MAP or matching active iPhone notifications alongside the phone number. Keep name associations in memory only and clear the displayed name if the number is edited.
- Require review of one phone number and up to 160 characters, followed by Send once. No automatic retries, polling, saved drafts, or reply history.
- Validate the notification session, original content, and bonded/associated iPhone again before submission. Report phone acceptance separately from confirmed delivery.
- Handle explicit UTC/offset timestamps and keep group detection conservative. Group conversations and email-address recipients remain unsupported.
- Add protocol tests for reply encoding, packet fragmentation, lost responses, automatic matching, duplicate texts, conflicting names, timestamp formats, and Unicode.
- User-confirmed integrated replies, automatic sender selection, and names on the tested Watch8/iPhone setup. A separate probe confirmed iMessage delivery. Photos returned no image data in two MAP tests and are not included.
- Retain the existing ANCS background link, reconnect behavior, dismissal settings, icon, and ADB association workaround.

## 0.5-link-recovery — 2026-11-15 baseline

- Replace stale disconnected GATT clients when an incoming iPhone BLE connection becomes available.
- Add a session-scoped 45-second setup watchdog for stalled registration/discovery/subscriptions.
- Validate recovery after watch and iPhone restarts on the tested setup.

## 0.4-ui-actions

- Compact home, notification cards, settings, and separate diagnostics.
- Bundled app-recognition artwork and individual native alerts.
- Persistent bounded dismissal hashes and cleanup.
- Optional supported phone dismissals and confirmed manual iOS actions.

## 0.3-reconnect

- Automatic reconnection and ANCS app-name lookup.
- Launcher icon and individual local dismissal handling.

## 0.2-background-probe

- Companion Device service and presence observation.
- Validate owner-ADB association as a workaround for blocked third-party watch approval.

## 0.1-probe

- Standalone ANCS advertising, pairing, subscriptions, and text notification proof of concept.
