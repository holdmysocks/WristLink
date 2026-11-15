import dev.wristlink.AncsAppParser;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class AncsAppParserTest {
    static void check(boolean value) { if (!value) throw new AssertionError(); }
    static byte[] response(String app, String name) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); bytes.write(1);
        bytes.writeBytes(app.getBytes(StandardCharsets.UTF_8)); bytes.write(0); bytes.write(0);
        byte[] text = name.getBytes(StandardCharsets.UTF_8); bytes.write(text.length & 255); bytes.write(text.length >> 8); bytes.writeBytes(text); return bytes.toByteArray();
    }
    static void rejects(Runnable action) { try { action.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError(); }
    public static void main(String[] args) {
        String app = "com.example.messages"; byte[] bytes = response(app,"Messages 🌎");
        for (int split=0;split<bytes.length;split++) {
            AncsAppParser parser = new AncsAppParser(app);
            check(parser.feed(Arrays.copyOfRange(bytes,0,split)) == null);
            check(parser.feed(Arrays.copyOfRange(bytes,split,bytes.length)).equals("Messages 🌎"));
        }
        check(new AncsAppParser(app).feed(response(app,"")).equals(""));
        rejects(() -> new AncsAppParser("wrong.app").feed(bytes));
        rejects(() -> new AncsAppParser(app).feed(Arrays.copyOf(bytes,bytes.length+1)));
        rejects(() -> new AncsAppParser(app).feed(new byte[8193]));
        System.out.println("PASS: app-name fragmentation, UTF-8, identity, empty name, trailing and oversized responses");
    }
}
