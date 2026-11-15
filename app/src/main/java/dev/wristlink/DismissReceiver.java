package dev.wristlink;

import android.content.*;

/** Local-only dismissal. Never sends an iPhone notification action. */
public final class DismissReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        MainActivity.Probe.get(context).dismiss(intent.getLongExtra("uid",-1),intent.getStringExtra("session"));
    }
}
