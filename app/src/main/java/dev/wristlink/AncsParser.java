package dev.wristlink;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded ANCS Get Notification Attributes response reassembler. */
public final class AncsParser {
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final long uid;
    private final int[] expected;
    public AncsParser(long uid, int... expected) { this.uid = uid; this.expected = expected.clone(); }
    public Map<Integer,String> feed(byte[] fragment) {
        if (bytes.size() + fragment.length > 8192) throw new IllegalArgumentException("Response too large");
        bytes.write(fragment, 0, fragment.length);
        byte[] b = bytes.toByteArray();
        if (b.length < 5) return null;
        if (b[0] != 0 || unsigned32(b, 1) != uid) throw new IllegalArgumentException("Unexpected response identity");
        int p = 5;
        Map<Integer,String> result = new LinkedHashMap<>();
        for (int id : expected) {
            if (b.length - p < 3) return null;
            if ((b[p++] & 255) != id) throw new IllegalArgumentException("Unexpected attribute order");
            int n = (b[p++] & 255) | ((b[p++] & 255) << 8);
            if (n > 4096) throw new IllegalArgumentException("Attribute too large");
            if (b.length - p < n) return null;
            result.put(id, new String(b, p, n, StandardCharsets.UTF_8)); p += n;
        }
        if (p != b.length) throw new IllegalArgumentException("Trailing response data");
        return result;
    }
    public static long unsigned32(byte[] b, int p) {
        return (b[p] & 255L) | ((b[p+1] & 255L) << 8) | ((b[p+2] & 255L) << 16) | ((b[p+3] & 255L) << 24);
    }
}
