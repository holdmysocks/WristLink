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

## Messages replies

- Enable Show Message Notifications for the watch in the iPhone Bluetooth settings. Confirm turning it off produces a visible MAP error without disrupting ANCS alerts.
- Receive a fresh test message; open Reply from the native notification, then separately from the in-app detail. Verify the sender choices, original-notification context, full selected number, and exact draft text.
- Test two people with similar or identical message text. Select each explicitly and verify replies reach the selected number. Do not treat contact-name similarity as address matching.
- Cancel or edit at review: no push occurs. Confirm Send once: recipient gets exactly one message. Check iMessage versus SMS routing on the phone.
- Reject blank/overlong text, control characters, unsupported email recipients, and invalid phone numbers. Verify number formatting and Unicode text.
- Dismiss the notification, reconnect the link, or pause WristLink while drafting; an expired context must not submit a message.
- Interrupt a send and check the displayed outcome. Never auto-retry; verify the phone/recipient before an explicit new attempt.
- Check a new incoming notification while the reply screen is open, then normal alerts/dismissals/reconnection afterward. The MAP socket must not disconnect the ANCS link.
- Close the reply activity and restart the process; no recipient, draft, body, or MAP history should remain in persistent app data. Diagnostics must contain no addresses or message contents.
- With replies idle, check that there is no MAP polling or persistent status notification. Repeat a normal-day battery check when comparing release battery use.

### Automatic matching and names

- Receive distinct texts from two saved contacts. Open Reply on each notification; confirm the correct name/number are filled without choosing a sender, then verify delivery after review.
- Send identical text from two numbers within five seconds. Verify the picker appears instead of automatically choosing one.
- Test hidden previews, old notifications outside the recent list, longer/truncated text, missing timestamps, grouped Messages, and email-address senders. Ambiguous/unsupported entries must not be silently selected.
- Verify known names from active iPhone notifications appear in the sender picker where the message match is unique. Unmatched entries should remain numbers rather than borrowing another sender's name.
- Edit the recipient number; the old name must disappear from the heading and final review. Verify no name associations survive process restart or get written to storage.
- If iOS updates a notification's content while a draft is open, submission must reject the changed context rather than reuse the old match.
- Check explicit UTC/offset MAP timestamps against local notification dates. A conversation name alone must not classify a one-to-one thread as a group.
- When a unique notification match establishes a sender name, older messages from that same number may reuse it in the current picker. A different number must not inherit it.
