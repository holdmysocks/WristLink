<img src="app/src/main/res/drawable-nodpi/wristlink_icon.png" width="96" alt="WristLink icon">

# WristLink

iPhone notifications on a standalone Galaxy Watch, without a downloaded iPhone companion app or subscription.

WristLink is a native Wear OS app that uses Apple's Notification Center Service (ANCS) over Bluetooth. It displays iPhone notifications in the watch's normal notification tray and keeps an in-app inbox for individual dismissal and available iOS actions.

## Status

**v0.5 is a stable personal baseline on one tested setup:** Samsung Galaxy Watch8 SM-L320 (Android API 36) and iPhone 15 Pro (iOS 27.0.1). Background notifications, range reconnection, both device restarts, individual watch alerts, and supported phone dismissal were tested on that setup. Normal-use battery life was reported good; no controlled drain measurement is claimed.

Other hardware and firmware combinations remain unverified. This is a sideloaded development build, not an official Samsung or Apple integration.

## Features

- Native watch notifications with sender/title/text and friendly app names.
- Bundled recognition symbols for Messages, Phone, Mail, Snapchat, and PayPal, plus a fallback for other apps.
- Background operation using an owner-created Companion Device association, without a persistent status notification on the tested watch.
- Automatic reconnection and ANCS resubscription, including recovery from a stale Bluetooth client.
- Individual watch dismissals that can survive reconnects.
- Optional **Dismiss on iPhone** setting, off by default, for supported Clear/Dismiss actions.
- Manual iOS-provided notification actions with confirmation.
- Compact home screen, inbox cards, settings, and separate diagnostics.

## Requirements

- A Wear OS watch running Android API 36 or later; current hardware validation is limited to Watch8.
- An iPhone with Bluetooth pairing and notification sharing enabled for the watch.
- A computer with Android platform tools (ADB) for installation and one-time background-association setup.

Google Play sign-in is not required to install the APK. An existing standalone watch configuration can be retained.

## Setup — including the watch's third-party association restriction

Download the tested APK from [GitHub Releases](https://github.com/holdmysocks/WristLink/releases/latest), or build from source below. Release APKs are debug-signed personal-baseline builds; updates need the same signing identity.

**Brand-new watch asking for an Android phone?** Start with [step 0: standalone onboarding](docs/SETUP.md#0-brand-new-watch-skip-android-phone-onboarding). It explains the Wear-icon tap-and-hold procedure and how to enable ADB afterward. Then complete the separate background-association workaround below.

The tested watch rejects the ordinary app-driven association flow with:

```text
3p apps are not allowed to create associations on watch.
```

An owner-authorized ADB command successfully creates the association instead:

```powershell
adb -s $WatchSerial shell cmd companiondevice associate 0 dev.wristlink $PhoneMac
```

`$PhoneMac` must be the **bonded iPhone's Bluetooth address**, not the watch address or Wi-Fi IP. Afterward, activate observation in WristLink and verify that Android binds its companion service. No root, reset, or bootloader unlock was required on the tested device.

**Follow [the complete setup and recovery guide](docs/SETUP.md).** It covers finding the correct address, activation, verification, and targeted removal. Creating the association record alone is not enough.

## Build

The repository includes Gradle 9.4.1 and AGP 9.2.1 configuration. Use Android SDK 36 and a compatible JDK; the tested Windows environment used Android Studio's JBR 25. Java source/target level is 17.

Configure `ANDROID_HOME`, or create an untracked `local.properties` with your own `sdk.dir`.

Windows:

```powershell
.\gradlew.bat --no-daemon --no-watch-fs -Dorg.gradle.native=false assembleDebug lintDebug
```

macOS/Linux:

```sh
./gradlew --no-daemon --no-watch-fs -Dorg.gradle.native=false assembleDebug lintDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. Dependencies require network access on the first build. Keep your signing keystore separately to update an existing installation in place; signing keys and APKs are intentionally excluded from Git.

## Tests

Standalone JVM tests cover ANCS response fragmentation, Unicode, response bounds, stable dismissal keys, and automatic-action semantics. They require a JDK and no Android device:

```powershell
.\scripts\Test-Protocol.ps1
```

```sh
./scripts/test-protocol.sh
```

CI runs these checks plus Android lint and a debug build. Device tests are still required for pairing, delivery, actions, reconnects, and battery behavior. See the [hardware checklist](docs/TESTING.md).

## Privacy and storage

WristLink declares no Internet permission and uses no cloud service. Notification text stays in memory, capped at 40 active entries. Diagnostic messages omit notification content and Bluetooth addresses.

Dismissals store SHA-256 keys and timestamps, not message text. Records are capped at 256 and seven days, cleaned on startup/session reset/new dismissals and periodically while the process runs. Known phone-removal events remove matching records; expired data is cleaned on the next run if the process was stopped.

## Limits

- ANCS exposes no photo-attachment preview or app-icon download. App symbols are bundled illustrative artwork.
- iOS can update a grouped notification under one ID; WristLink cannot split out messages that iOS does not expose separately.
- Persistent dismissal matching needs unchanged app/title/text/date attributes. Missing dates are session-only; changed content may be treated as new.
- Phone actions depend on what iOS offers for that notification. A successful request write does not prove completion. Arbitrary typed replies are not implemented.
- Native watch Clear all follows the saved Dismiss on iPhone preference; WristLink cannot reliably intercept it to ask a question first.
- Health integration, Play Store sign-in, full Samsung phone pairing, and call-audio implementation are outside scope. Native Bluetooth calling happened to work on the tested pairing and is not provided by WristLink.
- Association behavior on other firmware, force-stop, revoked permissions, and lost bonds need separate validation.

## Project layout

```text
app/src/main/       Android app, ANCS client, UI, icons
tests/             Standalone Java test programs
scripts/           Protocol test runners and optional ADB association helper
docs/              Setup/recovery and hardware test checklist
.github/workflows/ Build, lint, and protocol-test CI
```

See [CHANGELOG.md](CHANGELOG.md) for the baseline history. Keep personal logs, device identities, generated release archives, and research notes outside the repository. Use GitHub Releases for distributable APKs rather than committing them.

## License

Copyright (C) 2026 holdmysocks. Licensed under the **GNU General Public License, version 3 only (GPL-3.0-only)**. See [LICENSE](LICENSE). The software is provided without warranty. Third-party tooling, including the Gradle wrapper, retains its own license notices.
