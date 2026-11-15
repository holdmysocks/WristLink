package dev.wristlink;

import android.companion.CompanionDeviceService;

/** Bound by Android when it detects the user-approved Bluetooth companion's presence. */
public final class LinkCompanionService extends CompanionDeviceService {
    @Override public void onCreate() {
        super.onCreate();
        MainActivity.Probe.get(this).note("Companion service bound by Android.");
        MainActivity.Probe.get(this).resumeEnabled();
    }
    @Override public void onDevicePresenceEvent(android.companion.DevicePresenceEvent event) {
        if (event.getEvent() == android.companion.DevicePresenceEvent.EVENT_BT_CONNECTED || event.getEvent() == android.companion.DevicePresenceEvent.EVENT_BLE_APPEARED)
            MainActivity.Probe.get(this).resumeEnabled();
    }
    @Override public void onDestroy() {
        MainActivity.Probe.get(this).note("Companion service released by Android.");
        super.onDestroy();
    }
}
