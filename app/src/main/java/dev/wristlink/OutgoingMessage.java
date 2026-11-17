package dev.wristlink;

import java.nio.charset.StandardCharsets;

/** A single phone-number recipient and a short UTF-8 reply. No recipient inference. */
public final class OutgoingMessage {
  public static String number(String value) {
    String clean = value.replaceAll("[ ()-]", "");
    if (!clean.matches("\\+?[0-9]{7,15}"))
      throw new IllegalArgumentException(
          "Enter one phone number (7–15 digits, optional +). Email recipients are not supported in"
              + " this test.");
    return clean;
  }

  public static byte[] encode(String recipient, String text) {
    String phone = number(recipient);
    if (text.trim().isEmpty()
        || text.codePointCount(0, text.length()) > 160
        || text.codePoints().anyMatch(c -> Character.isISOControl(c)))
      throw new IllegalArgumentException("Enter a single-line reply of 1–160 characters.");
    String escaped = text.replace("END:MSG", "/END:MSG");
    int length = escaped.getBytes(StandardCharsets.UTF_8).length + 22;
    String wire =
        "BEGIN:BMSG\r\n"
            + "VERSION:1.0\r\n"
            + "STATUS:UNREAD\r\n"
            + "TYPE:SMS_GSM\r\n"
            + "FOLDER:telecom/msg/outbox\r\n"
            + "BEGIN:BENV\r\n"
            + "BEGIN:VCARD\r\n"
            + "VERSION:2.1\r\n"
            + "N:\r\n"
            + "TEL:"
            + phone
            + "\r\nEND:VCARD\r\nBEGIN:BBODY\r\nCHARSET:UTF-8\r\nLENGTH:"
            + length
            + "\r\nBEGIN:MSG\r\n"
            + escaped
            + "\r\nEND:MSG\r\nEND:BBODY\r\nEND:BENV\r\nEND:BMSG\r\n";
    byte[] bytes = wire.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > 1024)
      throw new IllegalArgumentException("Reply exceeds the transfer limit.");
    return bytes;
  }
}
