import dev.wristlink.AncsParser;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class AncsParserTest {
    static byte[] response() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{0,(byte)0xff,(byte)0xff,(byte)0xff,(byte)0xff});
        int[] ids = {0,1,3}; String[] values = {"com.test", "Hello 🌍", ""};
        for (int i=0;i<ids.length;i++) {
            byte[] text = values[i].getBytes(StandardCharsets.UTF_8);
            out.write(ids[i]); out.write(text.length & 255); out.write(text.length >> 8); out.writeBytes(text);
        }
        return out.toByteArray();
    }
    static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    static void rejects(Runnable body) { try { body.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("Expected rejection"); }
    public static void main(String[] args) {
        byte[] response = response();
        // Every possible two-fragment split, including inside the multibyte emoji.
        for (int split=0;split<response.length;split++) {
            AncsParser p = new AncsParser(0xffffffffL,0,1,3);
            check(p.feed(Arrays.copyOfRange(response,0,split)) == null);
            Map<Integer,String> data = p.feed(Arrays.copyOfRange(response,split,response.length));
            check(data.get(1).equals("Hello 🌍") && data.get(3).isEmpty());
        }
        AncsParser bytes = new AncsParser(0xffffffffL,0,1,3);
        Map<Integer,String> complete = null;
        for (byte b : response) complete = bytes.feed(new byte[]{b});
        check(complete != null && complete.get(0).equals("com.test"));
        rejects(() -> new AncsParser(1,0,1,3).feed(response));
        byte[] wrong = response.clone(); wrong[5] = 9;
        rejects(() -> new AncsParser(0xffffffffL,0,1,3).feed(wrong));
        byte[] huge = response.clone(); huge[6] = (byte)255; huge[7] = 127;
        rejects(() -> new AncsParser(0xffffffffL,0,1,3).feed(huge));
        rejects(() -> new AncsParser(0,0).feed(new byte[8193]));
        rejects(() -> new AncsParser(0xffffffffL,0,1,3).feed(Arrays.copyOf(response,response.length+1)));
        System.out.println("PASS: fragmentation, UTF-8, unsigned UID, empty attributes, identity/order/length bounds, trailing bytes");
    }
}
