# Hardware regression checklist

Record watch model/firmware/API, iPhone model/iOS, app version, and which steps were tested. Avoid placing device addresses, notification text, or unredacted logs in public issues.

- Fresh notification while WristLink is open; verify app name and content.
- Native tray alert while the watch is at its face and screen asleep for several minutes.
- Multiple apps and existing queued iPhone notifications; pre-existing entries should load quietly.
- Individual swipe dismissal removes the in-app entry without restoring it on a label refresh.
- Dismiss on iPhone off: phone alert remains. Reconnect and check unchanged dismissed watch alerts stay hidden where date matching is available.
- A new message still appears after an older one was dismissed.
- Dismiss on iPhone on: use a disposable alert with a Clear/Dismiss action. Check both devices. Test native Clear all separately.
- Inspect manual iOS labels and confirm only an action you intend to perform. A successful write is not proof of the remote result.
- Walk out of range and return without opening WristLink or manually reconnecting. Verify ANCS resubscription and a fresh notification.
- Restart the watch, then receive a notification without opening WristLink.
- Restart the iPhone; check automatic recovery and fresh delivery.
- Check Bluetooth toggles, app-process loss, permissions, and bond changes separately; do not infer them from range/restart success.
- Normal-day idle/battery check with Wireless debugging off. Compare like-for-like usage before claiming a drain percentage.
- Confirm dismissal hashes remain bounded/expire and no plaintext notification history is written to persistent storage.

Force-stop, other watch firmware, and other iOS combinations remain unverified unless explicitly tested. Native Bluetooth call behavior is separate from WristLink's ANCS client.
