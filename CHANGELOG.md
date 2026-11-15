# Changelog

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
