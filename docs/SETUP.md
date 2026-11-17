# Installation, ADB association workaround, and recovery

## Three separate connections/permissions

Bluetooth pairing, iPhone notification-sharing authorization, and the watch-side Companion Device association are separate. A phone showing “Connected” does not prove that WristLink's ANCS subscriptions or background service are ready.

On the validated Watch8, the normal association approval flow rejects third-party apps. The ADB shell command below bypasses that approval flow for this app and phone. It is not root or a blanket security-policy change. Availability on other firmware is unverified.

## 0. Brand-new watch: skip Android-phone onboarding

**Already at the watch face? Skip this step. Do not reset a working watch to repeat onboarding.**

For a new watch showing the phone-setup screens:

1. Power on and swipe up from the welcome screen.
2. At the phone-pairing/setup screen, repeatedly tap the **Wear icon near the top** until a prompt asks you to hold the watch icon for more than three seconds.
3. After the prompt disappears and the icon returns, press and hold that same icon for **more than three seconds**.
4. Release when **Starting** (or its localized equivalent) appears, then wait for standalone startup to finish.

[Samsung's standalone-setup instructions](https://www.samsung.com/de/support/mobile-devices/galaxy-watch-ohne-smartphone-einrichten/) describe this method for Wear OS Galaxy Watch4 and later. We did not repeat fresh onboarding during WristLink testing. Region/carrier/firmware availability can differ; [Samsung's US guidance](https://www.samsung.com/us/support/answer/ANS10003455/) is more restrictive. If the icon/prompt is absent, compatibility of this bypass with that firmware is unverified.

Standalone startup does **not** complete Google-account/Play Store sign-in or carrier eSIM setup. WristLink is installed with ADB afterward; the Companion Device association bypass in step 4 is a separate operation.

### Prepare the watch for ADB

1. In watch Settings > Connections > Wi-Fi, connect to the computer's reachable Wi-Fi network.
2. Open Settings > About watch > Software and tap **Software version five times**, or until the developer-mode confirmation appears.
3. Return to Settings > Developer options. Enable **ADB debugging** and **Wireless debugging**, then approve the debugging-network prompt for your chosen network.
4. Keep Wireless debugging open and continue below. Its pairing port/code and connection port are separate.

Menu labels can vary by One UI version. See [Samsung's developer connection guide](https://developer.samsung.com/health/sensor/guide/connect-watch.html).

## 1. Connect ADB

Put the computer and watch on reachable Wi-Fi. Enable Wireless debugging and keep that screen open. If the computer is not paired, use **Pair new device** and its temporary pairing address/code:

```powershell
adb pair WATCH_IP:PAIRING_PORT
```

Use the separate connection port on the main Wireless debugging screen afterward:

```powershell
adb connect WATCH_IP:CONNECTION_PORT
adb devices -l
adb mdns services
$WatchSerial = 'CURRENT_WATCH_IP:CURRENT_CONNECTION_PORT'
```

ADB must show `device`, not `offline` or `unauthorized`. An mDNS serial from `adb devices -l` is also valid. Use `-s` to select the watch if multiple devices/transports are listed. The IP/subnet and port can change after toggling debugging or restarting; do not reuse an old port blindly.

## 2. Install and pair with the iPhone

After building from the repository root:

```powershell
adb -s $WatchSerial install -r .\app\build\outputs\apk\debug\app-debug.apk
adb -s $WatchSerial shell am start -n dev.wristlink/.MainActivity --ez connect true
```

Allow Nearby devices and notifications on the watch. Open iPhone Settings > Bluetooth and connect to the watch's existing Bluetooth name. Accept pairing and notification sharing; check **Share System Notifications** in the device details if offered.

Look for connected status in WristLink or `ANCS ready.` in its redacted logs:

```powershell
adb -s $WatchSerial logcat -d -s WristLink:I AndroidRuntime:E
```

Retain the working standalone setup. Do not reset or unpair devices or remove Samsung/Google packages to install WristLink.

## 3. Find the bonded iPhone MAC

```powershell
adb -s $WatchSerial shell dumpsys bluetooth_manager
```

Find the connected, bonded iPhone. On the tested watch the bonded summary masked part of the address, while the matching **Devices in DB** row contained the full Bluetooth address. Use that matching iPhone row, not the watch's MAC, Wi-Fi address, service UUID, or an arbitrary nearby device. Resolve ambiguity before proceeding if several iPhones are listed.

```powershell
$PhoneMac = 'AA:BB:CC:DD:EE:FF' # Replace with the actual bonded iPhone address.
```

This example MAC is a placeholder. Device identities can change after forgetting/re-pairing or resetting a device. Android's stored bonded identity may differ from a rotating BLE privacy address seen over the air.

## 4. Bypass the third-party watch approval restriction

Inspect existing state first:

```powershell
adb -s $WatchSerial shell cmd companiondevice list 0
adb -s $WatchSerial shell dumpsys companiondevice
```

Reuse an existing correct `dev.wristlink` association. If none exists, the command that worked on the tested firmware is:

```powershell
adb -s $WatchSerial shell cmd companiondevice associate 0 dev.wristlink $PhoneMac
```

`0` is the ordinary watch owner's Android user ID. Do not create duplicates or use another app's package name. Supported commands can be inspected with:

```powershell
adb -s $WatchSerial shell cmd companiondevice help
```

The optional helper checks device state and existing associations before creating one:

```powershell
.\scripts\Associate-WristLink.ps1 -WatchSerial $WatchSerial -PhoneMac $PhoneMac
```

If local PowerShell script execution is disabled, use the manual ADB commands. No execution-policy change is needed. The helper does not install the app, change bonds, or activate background observation.

## 5. Activate and verify background observation

After creating the record, open WristLink > Settings. Tap **Pause connection**, then **Connect to iPhone**. If already paused, just connect. This registers observation against the new association and restores ANCS; it does not remove the bond or association.

```powershell
adb -s $WatchSerial shell dumpsys companiondevice
adb -s $WatchSerial logcat -d -s WristLink:I AndroidRuntime:E
```

While the phone is present, verify:

```text
mPackageName='dev.wristlink'
mDeviceMacAddress=<the bonded iPhone address>
mNotifyOnDeviceNearby=true
Connected Bluetooth Devices: ... pkg=u0/dev.wristlink
Bound Companion Applications: <u0, dev.wristlink>, isPrimary=true
Companion service bound by Android.
ANCS ready.
```

A record alone is insufficient. `mNotifyOnDeviceNearby=false` means observation is inactive. Binding can legitimately disappear while the phone is absent. Return to the watch face, let it sleep, and receive a fresh notification to validate background delivery.

Turn Wireless debugging off after setup. It is not needed for daily use. Normal range loss or restarts do not require re-creating the association.

Do not use `simulate-device-appeared` as the ongoing workaround: the firmware describes it as a temporary 60-second debugging simulation. The working setup uses a real association and real device observation. Do not use `disassociate-all` or blanket permission/package changes.

## Dismissals and actions

Swiping one watch alert also clears its in-app entry. With **Dismiss on iPhone** off, it stays on the phone. With the setting on, WristLink requests a phone negative action only when explicitly labeled Clear or Dismiss, excluding incoming calls. Other labels remain manual. Open a notification for the exact iOS-provided actions and confirm the desired action.

Saved dismissal hashes can suppress unchanged old notifications after reconnect. Fresh messages with different content/date remain eligible for alerts. iOS grouping and missing dates limit this matching.

## Messages replies

In iPhone Settings > Bluetooth > the watch's information button, enable **Show Message Notifications**. This is separate from **Share System Notifications**, which authorizes the ANCS alert link. The tested phone initially rejected MAP access until Show Message Notifications was enabled.

Receive a fresh text and tap **Reply** on its native watch notification or in WristLink's notification details. WristLink looks up recent messages and opens a draft when it finds one unique text/time match. Otherwise, choose the sender or enter a phone number. Names come from the phone's MAP data or matching active iPhone notifications, so an unmatched sender may still display a number.

Enter up to 160 characters, tap **Review reply**, check the name/number and exact text, then confirm **Send once**. Replies go to one phone number; group conversations and email-address recipients are not supported. A successful iMessage delivery was confirmed on the tested pairing, but routing depends on the phone. “Reply accepted” reports phone acceptance, not recipient delivery. Check the phone and recipient before retrying an uncertain send; WristLink never retries automatically.

No additional iPhone app or watch test app is needed. MAP runs only for sender lookup and confirmed sending; it does not replace ANCS, poll in the background, or save drafts/contacts/reply history. Photo previews are not included.

If the notification changes or the Bluetooth notification session ends while drafting, start again from a current notification. This prevents submitting a reply against an expired context.

### Remove the optional research probe

If you installed **WristLink MAP Probe** during development, it can now be uninstalled from the watch's Apps settings. The integrated app is **WristLink** (`dev.wristlink`); keep that installed.

Optional ADB equivalent:

```powershell
adb -s $WatchSerial uninstall dev.wristlink.mapprobe
```

Removing `dev.wristlink.mapprobe` does not remove WristLink's settings, association, or Bluetooth bond. Do not uninstall `dev.wristlink`, clear its data, or remove its association for this cleanup.

## Recovery

1. For ADB failures, verify the current IP/subnet/connection port and `adb devices -l` state.
2. Verify the correct association, `mNotifyOnDeviceNearby=true`, and bound companion service while the phone is present.
3. Check watch notification permission and iPhone notification sharing.
4. Check logs for ANCS ready. v0.5 replaces a stale disconnected client when the phone opens a new incoming BLE link and has a 45-second setup watchdog; allow recovery time.
5. If needed, pause/connect from WristLink settings. Do not unpair or clear app data as the first recovery step.
6. Recreate the association after uninstall/reset if it was removed. Same-key `adb install -r` updates preserve the existing app state; a differently signed APK cannot update it in place.

## Remove only WristLink's association

Pause WristLink first, then intentionally remove its record:

```powershell
adb -s $WatchSerial shell cmd companiondevice disassociate 0 dev.wristlink $PhoneMac
adb -s $WatchSerial shell dumpsys companiondevice
```

This does not request Bluetooth unpairing. Background support from the association is lost until it is recreated and observation is activated.

## References

- [Apple ANCS specification](https://developer.apple.com/library/archive/documentation/CoreBluetooth/Reference/AppleNotificationCenterServiceSpecification/Specification/Specification.html)
- [Android BLE background guidance](https://developer.android.com/develop/connectivity/bluetooth/ble/background)
- [AOSP watch-side association restriction](https://android.googlesource.com/platform/frameworks/base.git/+/09a38e4e8c64ccf62573da7ffbe40cef90e573ae/services/companion/java/com/android/server/companion/association/AssociationRequestsProcessor.java)

Firmware command help, verified association/service state, and real hardware tests established this workaround. The presence of an API alone does not guarantee compatibility.
