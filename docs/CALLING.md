# Native iPhone calls on Galaxy Watch8

Native answering and two-way audio were observed on a standalone Galaxy Watch8 SM-L320 (Android 16, firmware L320XXS2AZF4 at the recovery test) paired directly to an iPhone 15 Pro. Samsung Phone provides the answer screen. The Bluetooth Hands-Free Profile (HFP) carries call controls and audio. WristLink's notification and Messages connections can work while HFP is disconnected.

## Restore Samsung Phone

If you previously disabled preinstalled watch apps, re-enable Samsung Phone. Run these commands against the connected watch after following the [ADB setup guide](SETUP.md#1-connect-adb).

```powershell
adb -s $WatchSerial shell pm enable --user 0 com.samsung.android.dialer
adb -s $WatchSerial shell cmd role get-role-holders --user 0 android.app.role.DIALER
```

On the tested watch, Samsung Phone became the default dialer again after re-enabling it. Turning off the whole package removes the in-call interface. Keep the package enabled if you use native calling.

## Reconnect Hands-Free

If Galaxy Watch8 is absent from the iPhone's **Audio** list during an ordinary phone call, check the Bluetooth pairing and try the optional helper. It uses your existing owner-authorized ADB connection and the watch's Bluetooth service. It selects a single already bonded peer with Hands-Free and Message Access services and stops if there is no unique match.

From the repository root, with ADB, Node.js, a JDK with `javac` and `jar`, Android SDK Platform 36, and Build Tools 36.0.0 installed, set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) to the SDK path. Use the serial reported by `adb devices -l`.

```powershell
node scripts/watch-calls.cjs $WatchSerial status
node scripts/watch-calls.cjs $WatchSerial connect
```

State `2` means connected. A status check exits with code `3` if Hands-Free is disconnected. The helper builds a temporary JAR on the computer, runs it once as the ADB shell user, and removes it after each run. It does not need root or an iPhone app. The Android profile APIs and shell permissions may differ across watch firmware.

You can optionally save the Hands-Free preference using `node scripts/watch-calls.cjs $WatchSerial enable`. Policy `100` means allowed. That preference persisted through a watch Bluetooth restart in testing, but the profile stayed disconnected until we ran `connect` again. Automatic recovery is unverified after other disconnects and restarts. Verify by answering an incoming call from Samsung's green answer control and checking both the watch microphone and speaker.

## Phone icon in the Apps screen

Samsung's app-hiding feature is unavailable on Wear OS. This Watch8 also rejected an ADB change to disable just the Phone launcher activity. Leave Phone enabled for calling. If your launcher supports moving icons, move Phone out of the way instead. [Samsung app management guide](https://www.samsung.com/us/support/answer/ANS10002865/#:~:text=Hiding%20unwanted%20watch%20apps)
