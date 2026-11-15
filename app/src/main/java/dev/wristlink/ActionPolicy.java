package dev.wristlink;

import java.util.Locale;

/** Negative actions are not universally dismissals (an incoming call may say Decline). */
public final class ActionPolicy {
    public static boolean canAutoDismiss(int category, boolean negative, String label) {
        if (!negative || category == 1 || label == null) return false;
        String value = label.strip().toLowerCase(Locale.ROOT);
        return value.equals("clear") || value.equals("dismiss");
    }
}
