package dev.wristlink;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Stable across ANCS session UIDs; no plaintext notification content is persisted. */
public final class DismissalKey {
    public static String of(String app, String title, String message, String date) {
        if (date == null || date.isBlank()) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : new String[]{app,title,message,date}) {
                byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
                digest.update(new byte[]{(byte)(bytes.length>>24),(byte)(bytes.length>>16),(byte)(bytes.length>>8),(byte)bytes.length});
                digest.update(bytes);
            }
            StringBuilder result = new StringBuilder(); for (byte b : digest.digest()) result.append(String.format(java.util.Locale.ROOT,"%02x",b & 255));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
