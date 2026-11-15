package dev.wristlink;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Get App Attributes response: command 1, NUL-terminated app ID, then display-name tuple. */
public final class AncsAppParser {
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final String app;
    public AncsAppParser(String app) { this.app = app; }
    public String feed(byte[] fragment) {
        if (bytes.size() + fragment.length > 8192) throw new IllegalArgumentException("Response too large");
        bytes.write(fragment,0,fragment.length);
        byte[] b = bytes.toByteArray();
        if (b.length == 0) return null;
        if (b[0] != 1) throw new IllegalArgumentException("Unexpected command");
        int p = 1; while (p < b.length && b[p] != 0) p++;
        if (p == b.length) return null;
        if (!new String(b,1,p-1,StandardCharsets.UTF_8).equals(app)) throw new IllegalArgumentException("Unexpected app");
        p++;
        if (b.length-p < 3) return null;
        if (b[p++] != 0) throw new IllegalArgumentException("Unexpected attribute");
        int n = (b[p++] & 255) | ((b[p++] & 255) << 8);
        if (n > 4096) throw new IllegalArgumentException("Name too large");
        if (b.length-p < n) return null;
        if (b.length-p != n) throw new IllegalArgumentException("Trailing data");
        return new String(b,p,n,StandardCharsets.UTF_8);
    }
}
