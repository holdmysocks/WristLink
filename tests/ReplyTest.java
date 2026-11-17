import dev.wristlink.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ReplyTest {
  static void check(boolean ok) {
    if (!ok) throw new AssertionError();
  }

  static byte[] packet(int code, byte[] body) {
    int n = body.length + 3;
    return Obex.join(new byte[] {(byte) code, (byte) (n >> 8), (byte) n}, body);
  }

  static void rejects(String number, String text) {
    try {
      OutgoingMessage.encode(number, text);
    } catch (IllegalArgumentException expected) {
      return;
    }
    throw new AssertionError();
  }

  public static void main(String[] args) throws Exception {
    byte[] message = OutgoingMessage.encode("+1 (202) 555-0143", "Test 🌎 " + "a".repeat(145));
    String wire = new String(message, StandardCharsets.UTF_8);
    check(wire.contains("TEL:+12025550143\r\n"));
    check(wire.contains("TYPE:SMS_GSM\r\n"));
    int start = wire.indexOf("BEGIN:MSG\r\n"), end = wire.indexOf("END:BBODY");
    int declared = Integer.parseInt(wire.substring(wire.indexOf("LENGTH:") + 7, start).trim());
    check(wire.substring(start, end).getBytes(StandardCharsets.UTF_8).length == declared);
    rejects("x@example.com", "Test");
    rejects("123\r\nTEL:5551212", "Test");
    rejects("911", "Test");
    rejects("+12025550143", "");
    rejects("+12025550143", "a".repeat(161));
    rejects("+12025550143", "Test\nEND:BMSG");
    byte[] connected = packet(0xa0, new byte[] {0x10, 0, 0, (byte) 255, (byte) 0xcb, 0, 0, 0, 7});
    ByteArrayOutputStream server = new ByteArrayOutputStream();
    server.writeBytes(connected);
    server.writeBytes(packet(0x90, new byte[0]));
    int chunks = (message.length + 243) / 244;
    for (int i = 0; i < chunks; i++)
      server.writeBytes(packet(i == chunks - 1 ? 0xa0 : 0x90, new byte[0]));
    ByteArrayOutputStream sent = new ByteArrayOutputStream();
    Obex client = new Obex(new ByteArrayInputStream(server.toByteArray()), sent);
    client.connect();
    check(client.push(message).code == 0xa0);
    ByteArrayInputStream requests = new ByteArrayInputStream(sent.toByteArray());
    check(Obex.read(requests, 4).code == 0x80);
    Obex.Reply headers = Obex.read(requests, 0);
    check(headers.code == 0x02);
    check(
        Arrays.equals(headers.headers.get(0x4c), new byte[] {0x0b, 1, 0, 0x0c, 1, 0, 0x14, 1, 1}));
    ByteArrayOutputStream collected = new ByteArrayOutputStream();
    for (int i = 0; i < chunks; i++) {
      Obex.Reply part = Obex.read(requests, 0);
      check(part.code == (i == chunks - 1 ? 0x82 : 0x02));
      collected.writeBytes(part.body);
    }
    check(Arrays.equals(collected.toByteArray(), message));
    check(requests.available() == 0);
    sent.reset();
    client =
        new Obex(new ByteArrayInputStream(Obex.join(connected, packet(0xc3, new byte[0]))), sent);
    client.connect();
    check(client.push(message).code == 0xc3);
    requests = new ByteArrayInputStream(sent.toByteArray());
    Obex.read(requests, 4);
    check(Obex.read(requests, 0).code == 0x02);
    check(requests.available() == 0);
    sent.reset();
    client =
        new Obex(new ByteArrayInputStream(Obex.join(connected, packet(0x90, new byte[0]))), sent);
    client.connect();
    try {
      client.push(message);
      throw new AssertionError();
    } catch (EOFException expected) {
    }
    requests = new ByteArrayInputStream(sent.toByteArray());
    Obex.read(requests, 4);
    Obex.read(requests, 0);
    Obex.read(requests, 0);
    check(requests.available() == 0);
    System.out.println(
        "PASS: recipient/text validation, UTF-8 bMessage lengths, negotiated packet fragmentation,"
            + " outbox/retry-off headers, rejected pushes, no replay after lost response");
  }
}
