package dev.wristlink;

import android.annotation.SuppressLint;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.xmlpull.v1.*;

/** User-driven MAP replies. No background polling, draft persistence, or automatic sending. */
@SuppressLint("MissingPermission")
public final class ReplyActivity extends Activity {
  private static final UUID MAS = UUID.fromString("00001132-0000-1000-8000-00805f9b34fb");
  private static final AtomicBoolean MAP_BUSY = new AtomicBoolean();
  private final Handler ui = new Handler(Looper.getMainLooper());
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private volatile BluetoothSocket socket;
  private volatile boolean closed;
  private boolean busy, attempted;
  private long uid, epoch;
  private String session,
      originalTitle,
      originalText,
      originalDate,
      recipientName = "",
      namedNumber = "";
  private BluetoothDevice phone;
  private ScrollView scroll;
  private LinearLayout column;
  private EditText recipientInput, messageInput;

  static boolean supports(String app) {
    return "com.apple.MobileSMS".equals(app);
  }

  static Intent intent(Context context, long uid, String session) {
    return new Intent(context, ReplyActivity.class)
        .setData(android.net.Uri.parse("wristlink://reply/" + session + "/" + uid))
        .putExtra("uid", uid)
        .putExtra("session", session);
  }

  private int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private android.graphics.drawable.GradientDrawable surface(int color) {
    android.graphics.drawable.GradientDrawable s = new android.graphics.drawable.GradientDrawable();
    s.setColor(color);
    s.setCornerRadius(dp(22));
    return s;
  }

  private void add(View view) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = dp(10);
    column.addView(view, lp);
  }

  private TextView text(String value, int size) {
    TextView v = new TextView(this);
    v.setText(value);
    v.setTextSize(size);
    v.setTextColor(0xffe3edf5);
    v.setGravity(Gravity.CENTER);
    add(v);
    return v;
  }

  private Button button(String label, boolean primary, Runnable action) {
    Button b = new Button(this);
    b.setText(label);
    b.setAllCaps(false);
    b.setTextSize(14);
    b.setMinHeight(dp(46));
    b.setMinimumHeight(dp(46));
    b.setTextColor(primary ? 0xff001b25 : 0xffe3edf5);
    b.setBackground(surface(primary ? 0xff57dce8 : 0xff1d2c38));
    b.setPadding(dp(12), dp(10), dp(12), dp(10));
    b.setOnClickListener(v -> action.run());
    add(b);
    return b;
  }

  private void page(String title) {
    clearDraft();
    column.removeAllViews();
    text(title, 20);
    scroll.post(() -> scroll.scrollTo(0, 0));
  }

  private void clearDraft() {
    if (recipientInput != null) recipientInput.setText("");
    if (messageInput != null) messageInput.setText("");
    recipientInput = null;
    messageInput = null;
  }

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(0xff050e15);
    column = new LinearLayout(this);
    column.setOrientation(LinearLayout.VERTICAL);
    column.setPadding(dp(26), dp(24), dp(26), dp(34));
    scroll.addView(column);
    setContentView(scroll);
    getOnBackInvokedDispatcher()
        .registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::finish);
    uid = getIntent().getLongExtra("uid", -1);
    session = getIntent().getStringExtra("session");
    try {
      MainActivity.Probe p = MainActivity.Probe.get(this);
      MainActivity.Probe.Entry e = current();
      if (e == null || !p.ready || p.gatt == null) throw new IOException();
      phone = p.gatt.getDevice();
      BluetoothDevice trusted = p.trustedPeer();
      if (phone.getBondState() != BluetoothDevice.BOND_BONDED
          || trusted == null
          || !trusted.equals(phone)) throw new IOException();
      originalTitle = e.title;
      originalText = e.message;
      originalDate = e.date;
      loadInbox(true);
    } catch (Exception unavailable) {
      expired();
    }
  }

  private MainActivity.Probe.Entry current() {
    MainActivity.Probe p = MainActivity.Probe.get(this);
    if (session == null || !session.equals(p.notificationSession)) return null;
    MainActivity.Probe.Entry e = p.entries.get(uid);
    return e != null && supports(e.app) ? e : null;
  }

  private boolean valid() {
    try {
      MainActivity.Probe p = MainActivity.Probe.get(this);
      MainActivity.Probe.Entry e = current();
      return e != null
          && Objects.equals(originalTitle, e.title)
          && Objects.equals(originalText, e.message)
          && Objects.equals(originalDate, e.date)
          && p.ready
          && p.gatt != null
          && phone != null
          && phone.equals(p.gatt.getDevice())
          && phone.equals(p.trustedPeer())
          && phone.getBondState() == BluetoothDevice.BOND_BONDED;
    } catch (RuntimeException unavailable) {
      return false;
    }
  }

  private void expired() {
    page("Reply unavailable");
    text("Open a current Messages notification while WristLink is connected to your iPhone.", 14);
    button("Close", false, this::finish);
  }

  private static String attr(XmlPullParser p, String name) {
    String value = p.getAttributeValue(null, name);
    return value == null ? "" : value.substring(0, Math.min(value.length(), 512));
  }

  private void loadInbox() {
    loadInbox(false);
  }

  private void loadInbox(boolean automatic) {
    if (busy || attempted) return;
    if (!valid()) {
      expired();
      return;
    }
    final ArrayList<ReplyMatcher.Notice> notices = new ArrayList<>();
    for (MainActivity.Probe.Entry e : MainActivity.Probe.get(this).entries.values())
      if (supports(e.app)) notices.add(new ReplyMatcher.Notice(e.title, e.message, e.date));
    page("Finding messages…");
    text("Looking up recent senders on your iPhone.", 14);
    button("Cancel", false, this::finish);
    runTask(
        false,
        (client, state, token) -> {
          Obex.Reply r =
              client.get(
                  Obex.join(
                      Obex.name("inbox"),
                      Obex.type("x-bt/MAP-msg-listing"),
                      Obex.header(0x4c, new byte[] {1, 2, 0, 10, 0x13, 1, (byte) 255})));
          require("Inbox listing", r);
          XmlPullParser parser = android.util.Xml.newPullParser();
          parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false);
          parser.setInput(new ByteArrayInputStream(r.body), "UTF-8");
          ArrayList<ReplyMatcher.Candidate> entries = new ArrayList<>();
          for (int event = parser.getEventType();
              event != XmlPullParser.END_DOCUMENT;
              event = parser.next()) {
            if (event == XmlPullParser.DOCDECL) throw new IOException();
            if (event == XmlPullParser.START_TAG && parser.getName().equals("msg")) {
              if (entries.size() >= 10) break;
              String recipients = attr(parser, "recipient_addressing");
              boolean group = ReplyMatcher.groupRecipients(recipients);
              entries.add(
                  new ReplyMatcher.Candidate(
                      attr(parser, "sender_addressing"),
                      attr(parser, "sender_name"),
                      attr(parser, "subject"),
                      attr(parser, "datetime"),
                      group));
            }
          }
          int matched =
              automatic
                  ? ReplyMatcher.match(
                      new ReplyMatcher.Notice(originalTitle, originalText, originalDate), entries)
                  : -1;
          note(
              "Inbox candidates="
                  + entries.size()
                  + "; unique notification match="
                  + (matched >= 0));
          note(
              ReplyMatcher.diagnostics(
                  new ReplyMatcher.Notice(originalTitle, originalText, originalDate), entries));
          result(
              token,
              () -> {
                if (!valid()) {
                  expired();
                  return;
                }
                if (matched >= 0) {
                  ReplyMatcher.Candidate candidate = entries.get(matched);
                  compose(
                      candidate.number,
                      candidate.subject,
                      ReplyMatcher.nameFor(matched, entries, notices));
                  return;
                }
                page("Choose sender");
                text("For: " + originalTitle + "\n" + originalText, 12);
                text(
                    "Replies go to one phone number. Group conversations and email recipients are"
                        + " not supported yet.",
                    12);
                for (int i = 0; i < entries.size(); i++) {
                  ReplyMatcher.Candidate candidate = entries.get(i);
                  String normalized;
                  try {
                    normalized = OutgoingMessage.number(candidate.number);
                  } catch (IllegalArgumentException unsupported) {
                    continue;
                  }
                  final String number = normalized;
                  final String name = ReplyMatcher.nameFor(i, entries, notices);
                  String label = name.isBlank() ? number : name + "\n" + number;
                  button(
                      label + "\n" + candidate.subject + "\n" + candidate.date,
                      false,
                      () -> compose(number, candidate.subject, name));
                }
                if (entries.isEmpty())
                  text("No recent senders returned. Receive a fresh test text and try again.", 14);
                button("Enter number instead", false, () -> compose("", "", ""));
                button("Refresh", false, this::loadInbox);
                button("Cancel", false, this::finish);
              });
        });
  }

  private void compose(String number, String subject, String name) {
    if (attempted) return;
    if (!valid()) {
      expired();
      return;
    }
    page("Write reply");
    recipientName = name;
    try {
      namedNumber = OutgoingMessage.number(number);
    } catch (IllegalArgumentException invalid) {
      namedNumber = "";
    }
    final TextView nameView = name.isBlank() ? null : text(name, 18);
    text(subject.isBlank() ? "One recipient" : "Replying to:\n" + subject, 12);
    recipientInput = new EditText(this);
    recipientInput.setTextColor(0xfff5f8fc);
    recipientInput.setHint("Phone number");
    recipientInput.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
    recipientInput.setSaveEnabled(false);
    recipientInput.setText(number);
    add(recipientInput);
    if (nameView != null)
      recipientInput.addTextChangedListener(
          new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            public void afterTextChanged(android.text.Editable value) {
              String number;
              try {
                number = OutgoingMessage.number(value.toString());
              } catch (IllegalArgumentException invalid) {
                number = "";
              }
              nameView.setText(number.equals(namedNumber) ? name : "Recipient");
            }
          });
    messageInput = new EditText(this);
    messageInput.setTextColor(0xfff5f8fc);
    messageInput.setHint("Reply text");
    messageInput.setSingleLine(true);
    messageInput.setSaveEnabled(false);
    add(messageInput);
    text("Up to 160 characters. Review the number and text before sending.", 12);
    button("Review reply", true, this::review);
    button("Choose another sender", false, this::loadInbox);
    button("Cancel", false, this::finish);
  }

  private void review() {
    if (busy || attempted || recipientInput == null || messageInput == null) return;
    if (!valid()) {
      expired();
      return;
    }
    try {
      final String number = OutgoingMessage.number(recipientInput.getText().toString());
      final String message = messageInput.getText().toString();
      final byte[] encoded = OutgoingMessage.encode(number, message);
      String name = number.equals(namedNumber) ? recipientName : "";
      AlertDialog dialog =
          new AlertDialog.Builder(this)
              .setTitle("Send reply?")
              .setMessage(
                  "To: "
                      + (name.isBlank() ? "" : name + "\n")
                      + number
                      + "\n\n"
                      + message
                      + "\n\nOne recipient through your iPhone.")
              .setNegativeButton("Edit", null)
              .setPositiveButton("Send once", (d, w) -> send(encoded))
              .create();
      dialog.setOnShowListener(
          d -> dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE));
      dialog.show();
    } catch (IllegalArgumentException invalid) {
      AlertDialog dialog =
          new AlertDialog.Builder(this)
              .setTitle("Check reply")
              .setMessage(invalid.getMessage())
              .setPositiveButton("OK", null)
              .create();
      dialog.setOnShowListener(
          d -> dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE));
      dialog.show();
    }
  }

  private void send(byte[] encoded) {
    if (busy || attempted) return;
    if (!valid()) {
      expired();
      return;
    }
    attempted = true;
    page("Sending reply…");
    text("One send attempt. Please wait for the result.", 14);
    button("Close", false, this::finish);
    runTask(
        true,
        (client, state, token) -> {
          FutureTask<Boolean> stillCurrent = new FutureTask<>(() -> !closed && valid());
          ui.post(stillCurrent);
          if (!stillCurrent.get(2, TimeUnit.SECONDS))
            throw new IOException("Reply context expired");
          state.putStarted = true;
          Obex.Reply r = client.push(encoded);
          require("PushMessage", r);
          note("PushMessage accepted; delivery unconfirmed.");
          result(
              token,
              () -> {
                page("Reply accepted");
                text(
                    "Your iPhone accepted the reply. Delivery is not confirmed by this response.",
                    14);
                button("Done", true, this::finish);
              });
        });
  }

  private interface Task {
    void run(Obex client, State state, long token) throws Exception;
  }

  private static final class State {
    boolean putStarted;
  }

  private static final class Rejected extends IOException {
    final int code;

    Rejected(int code) {
      this.code = code;
    }
  }

  private void require(String operation, Obex.Reply reply) throws IOException {
    note(operation + " response=0x" + Integer.toHexString(reply.code));
    if (reply.code != 0xa0) throw new Rejected(reply.code);
  }

  private void runTask(boolean sending, Task task) {
    if (busy) return;
    if (!MAP_BUSY.compareAndSet(false, true)) {
      page("Reply busy");
      text("Another message operation is running. Close it, then open this reply again.", 14);
      button("Close", false, this::finish);
      return;
    }
    busy = true;
    final long token = ++epoch;
    final State state = new State();
    worker.execute(() -> {
      BluetoothSocket attempt = null;
      Runnable timeout = null;
      try {
        if (closed) throw new InterruptedIOException();
        attempt = phone.createRfcommSocketToServiceRecord(MAS);
        socket = attempt;
        final BluetoothSocket bounded = attempt;
        timeout =
            () -> {
              try {
                bounded.close();
              } catch (IOException ignored) {
              }
            };
        ui.postDelayed(timeout, 45000);
        if (closed) throw new InterruptedIOException();
        attempt.connect();
        Obex client = new Obex(attempt.getInputStream(), attempt.getOutputStream());
        require("Connect", client.connect());
        require("Folder telecom", client.path("telecom"));
        require("Folder msg", client.path("msg"));
        if (closed) throw new InterruptedIOException();
        task.run(client, state, token);
      } catch (Exception error) {
        String code =
            error instanceof Rejected
                ? " (0x" + Integer.toHexString(((Rejected) error).code) + ")"
                : "";
        String outcome =
            !sending
                ? "Could not load recent Messages"
                    + code
                    + ". On iPhone, enable Show Message Notifications in this watch’s Bluetooth"
                    + " settings, then try again."
                : !state.putStarted
                    ? "The reply was not submitted. No automatic retry."
                    : error instanceof Rejected
                        ? "The iPhone rejected the reply" + code + ". No automatic retry."
                        : "The send result is unknown. Check your iPhone and the recipient before"
                              + " trying again. No automatic retry.";
        note(
            "Operation stopped: "
                + error.getClass().getSimpleName()
                + code
                + "; putStarted="
                + state.putStarted);
        result(
            token,
            () -> {
              page(sending ? "Reply result" : "Messages unavailable");
              text(outcome, 14);
              if (!sending) button("Try again", true, this::loadInbox);
              button("Close", false, this::finish);
            });
      } finally {
        if (timeout != null) ui.removeCallbacks(timeout);
        try {
          if (attempt != null) attempt.close();
        } catch (IOException ignored) {
        }
        socket = null;
        MAP_BUSY.set(false);
        ui.post(() -> busy = false);
      }
    });
  }

  private void result(long token, Runnable action) {
    ui.post(() -> {
      if (!closed && epoch == token) action.run();
    });
  }

  private static void note(String text) {
    android.util.Log.i("WristLinkReply", text);
  }

  @Override
  protected void onDestroy() {
    closed = true;
    epoch++;
    clearDraft();
    BluetoothSocket active = socket;
    if (active != null)
      try {
        active.close();
      } catch (IOException ignored) {
      }
    worker.shutdown();
    super.onDestroy();
  }
}
