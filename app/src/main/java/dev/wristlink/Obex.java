package dev.wristlink;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Bounded OBEX client. Push is explicit and never retried; message-status operations are absent.
 */
public final class Obex {
  private final InputStream input;
  private final OutputStream output;
  private byte[] connectionId = new byte[0];
  private int peerPacket = 255;

  public Obex(InputStream input, OutputStream output) {
    this.input = input;
    this.output = output;
  }

  public static byte[] join(byte[]... chunks) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (byte[] b : chunks) out.writeBytes(b);
    return out.toByteArray();
  }

  public static byte[] header(int id, byte[] value) {
    int n = value.length + 3;
    return join(new byte[] {(byte) id, (byte) (n >> 8), (byte) n}, value);
  }

  public static byte[] name(String value) {
    return header(1, join(value.getBytes(StandardCharsets.UTF_16BE), new byte[] {0, 0}));
  }

  public static byte[] type(String value) {
    return header(0x42, join(value.getBytes(StandardCharsets.US_ASCII), new byte[] {0}));
  }

  public static final class Reply {
    public final int code;
    public final Map<Integer, byte[]> headers;
    public final byte[] body, fixedData;

    Reply(int code, Map<Integer, byte[]> headers, byte[] body) {
      this(code, headers, body, new byte[0]);
    }

    Reply(int code, Map<Integer, byte[]> headers, byte[] body, byte[] fixedData) {
      this.code = code;
      this.headers = headers;
      this.body = body;
      this.fixedData = fixedData;
    }
  }

  public static Reply read(InputStream stream, int fixed) throws IOException {
    int code = stream.read(), hi = stream.read(), lo = stream.read();
    if (code < 0 || hi < 0 || lo < 0) throw new EOFException("Incomplete response header");
    int length = (hi << 8) | lo;
    if (length < 3 + fixed) throw new IOException("Invalid packet size");
    byte[] body = stream.readNBytes(length - 3);
    if (body.length != length - 3) throw new EOFException("Incomplete packet");
    Map<Integer, byte[]> headers = new HashMap<>();
    ByteArrayOutputStream content = new ByteArrayOutputStream();
    for (int p = fixed; p < body.length; ) {
      int id = body[p++] & 255, kind = id >> 6, n;
      if (kind < 2) {
        if (body.length - p < 2) throw new IOException("Header missing length");
        n = ((body[p] & 255) << 8) | (body[p + 1] & 255);
        p += 2;
        if (n < 3) throw new IOException("Bad header length");
        n -= 3;
      } else n = kind == 2 ? 1 : 4;
      if (n > body.length - p) throw new IOException("Header exceeds packet");
      if (id == 0x48 || id == 0x49) content.write(body, p, n);
      else headers.put(id, Arrays.copyOfRange(body, p, p + n));
      p += n;
    }
    return new Reply(code, headers, content.toByteArray(), Arrays.copyOf(body, fixed));
  }

  private Reply exchange(int op, byte[] data, int fixed) throws IOException {
    int n = data.length + 3;
    if (n > 4096) throw new IOException("Request too large");
    output.write(join(new byte[] {(byte) op, (byte) (n >> 8), (byte) n}, data));
    output.flush();
    return read(input, fixed);
  }

  public Reply connect() throws IOException {
    byte[] target = HexFormat.of().parseHex("bb582b40420c11dbb0de0800200c9a66");
    Reply r = exchange(0x80, join(new byte[] {0x10, 0, 0x10, 0}, header(0x46, target)), 4);
    peerPacket = Math.min(4096, ((r.fixedData[2] & 255) << 8) | (r.fixedData[3] & 255));
    if (peerPacket < 255) throw new IOException("Invalid peer packet size");
    byte[] id = r.headers.get(0xcb);
    if (id != null) connectionId = join(new byte[] {(byte) 0xcb}, id);
    return r;
  }

  public Reply path(String folder) throws IOException {
    return exchange(0x85, join(new byte[] {2, 0}, connectionId, name(folder)), 0);
  }

  public Reply push(byte[] message) throws IOException {
    if (message.length == 0 || message.length > 1024)
      throw new IllegalArgumentException("Invalid reply size");
    int size = message.length;
    byte[] headers =
        join(
            connectionId,
            name("outbox"),
            type("x-bt/message"),
            header(0x4c, new byte[] {0x0b, 1, 0, 0x0c, 1, 0, 0x14, 1, 1}),
            new byte[] {(byte) 0xc3, 0, 0, (byte) (size >> 8), (byte) size});
    if (headers.length + 3 > peerPacket) throw new IOException("Peer packet too small");
    Reply r = exchange(0x02, headers, 0);
    if (r.code == 0xa0) throw new IOException("Premature PUT success");
    if (r.code != 0x90) return r;
    int chunk = peerPacket - connectionId.length - 6;
    for (int offset = 0; offset < message.length; ) {
      int end = Math.min(offset + chunk, message.length);
      boolean last = end == message.length;
      r =
          exchange(
              last ? 0x82 : 0x02,
              join(
                  connectionId,
                  header(last ? 0x49 : 0x48, Arrays.copyOfRange(message, offset, end))),
              0);
      offset = end;
      if (last) {
        if (r.code == 0x90) throw new IOException("Incomplete final PUT response");
        return r;
      }
      if (r.code != 0x90) {
        if (r.code == 0xa0) throw new IOException("Premature PUT success");
        return r;
      }
    }
    throw new IOException("Missing PUT body");
  }

  public Reply get(byte[] headers) throws IOException {
    return get(headers, 131072, 128);
  }

  public static final class Limit extends IOException {
    Limit() {
      super("Response exceeded the preview transfer limit");
    }
  }

  public Reply get(byte[] headers, int maxBytes, int maxPackets) throws IOException {
    if (maxBytes < 1 || maxBytes > 4 * 1024 * 1024 || maxPackets < 1 || maxPackets > 2048)
      throw new IllegalArgumentException("Invalid transfer limit");
    Reply r = exchange(0x83, join(connectionId, headers), 0);
    ByteArrayOutputStream content = new ByteArrayOutputStream();
    Map<Integer, byte[]> combined = new HashMap<>();
    for (int i = 0; ; i++) {
      if (content.size() + r.body.length > maxBytes) throw new Limit();
      content.writeBytes(r.body);
      combined.putAll(r.headers);
      if (r.code != 0x90) return new Reply(r.code, combined, content.toByteArray());
      if (i >= maxPackets - 1) throw new Limit();
      r = exchange(0x83, connectionId, 0);
    }
  }
}
