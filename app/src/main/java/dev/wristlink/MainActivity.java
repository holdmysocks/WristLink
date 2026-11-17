package dev.wristlink;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.companion.*;
import android.os.*;
import android.view.WindowManager;
import android.widget.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;

public final class MainActivity extends Activity {
    private LinearLayout column;
    private ScrollView scroll;
    private String page = "home";
    private final Runnable refresh = () -> { if (!page.equals("settings") && !page.equals("diagnostics")) render(); };
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private android.graphics.drawable.GradientDrawable surface(int color, int radius) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(color); shape.setCornerRadius(dp(radius)); return shape;
    }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setGravity(android.view.Gravity.CENTER); return view;
    }
    private void gap(int height) { column.addView(new android.view.View(this),new LinearLayout.LayoutParams(1,dp(height))); }
    private void title(String value) { TextView label = text(value,20,0xfff5f8fc); label.setTypeface(null,android.graphics.Typeface.BOLD); column.addView(label); gap(6); }
    private Button button(String label, boolean primary, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setTextSize(14); button.setAllCaps(false);
        button.setTextColor(primary ? 0xff001b25 : 0xffe3edf5); button.setMinHeight(0); button.setMinimumHeight(0);
        button.setPadding(dp(10),0,dp(10),0); button.setBackground(surface(primary ? 0xff57dce8 : 0xff1d2c38,24));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1,dp(46)); layout.topMargin=dp(8); button.setLayoutParams(layout);
        button.setOnClickListener(v -> action.run()); column.addView(button); return button;
    }
    private void navigate(String next) { page=next; scroll.scrollTo(0,0); render(); }
    private void render() {
        if (column == null) return;
        column.setPadding(dp(26),dp(page.equals("home") ? 18 : 24),dp(26),dp(page.equals("home") ? 18 : 34));
        if (page.equals("home")) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        int oldY = scroll.getScrollY(); column.removeAllViews(); Probe probe=Probe.get(this);
        if (page.equals("home")) {
            title("WristLink");
            column.addView(text(probe.ready ? "●  Connected to iPhone" : probe.running ? "●  Reconnecting…" : "Link paused",12,probe.ready ? 0xff57dce8 : 0xffadc0cf));
            gap(4);
            button("Notifications  ·  "+probe.entries.size(),true,() -> navigate("inbox"));
            button("Settings",false,() -> navigate("settings"));
        } else if (page.equals("inbox")) {
            title("Notifications");
            if (probe.entries.isEmpty()) { column.addView(text("You're all caught up",14,0xffadc0cf)); gap(12); }
            ArrayList<Map.Entry<Long,Probe.Entry>> items = new ArrayList<>(probe.entries.entrySet()); Collections.reverse(items);
            for (Map.Entry<Long,Probe.Entry> item : items) {
                Probe.Entry entry=item.getValue(); long uid=item.getKey();
                LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(12),dp(12),dp(12),dp(12)); card.setBackground(surface(0xff14232e,18));
                LinearLayout.LayoutParams cardLayout=new LinearLayout.LayoutParams(-1,-2); cardLayout.topMargin=dp(10); card.setLayoutParams(cardLayout);
                LinearLayout row = new LinearLayout(this); row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                ImageView icon=new ImageView(this); icon.setImageBitmap(AppIcons.bitmap(entry.app,probe.appName(entry.app))); row.addView(icon,new LinearLayout.LayoutParams(dp(24),dp(24)));
                TextView app=text(probe.appName(entry.app),12,0xff57dce8); app.setGravity(android.view.Gravity.START); app.setPadding(dp(8),0,0,0); row.addView(app,new LinearLayout.LayoutParams(0,-2,1)); card.addView(row);
                TextView heading=text(entry.title,14,0xfff5f8fc); heading.setGravity(android.view.Gravity.START); heading.setMaxLines(2); heading.setEllipsize(android.text.TextUtils.TruncateAt.END); heading.setPadding(0,dp(7),0,0); card.addView(heading);
                TextView body=text(entry.message,12,0xffadc0cf); body.setGravity(android.view.Gravity.START); body.setMaxLines(2); body.setEllipsize(android.text.TextUtils.TruncateAt.END); card.addView(body);
                card.setOnClickListener(v -> details(uid)); card.setContentDescription(probe.appName(entry.app)+", "+entry.title+", open notification"); column.addView(card);
            }
            gap(6); button("Back",false,() -> navigate("home"));
        } else if (page.equals("settings")) {
            title("Settings");
            Switch sync = new Switch(this); sync.setText("Dismiss on iPhone"); sync.setTextColor(0xfff5f8fc); sync.setTextSize(13); sync.setPadding(0,dp(8),0,dp(8));
            sync.setChecked(probe.syncDismiss()); sync.setOnCheckedChangeListener((v,checked) -> probe.context.getSharedPreferences("link",0).edit().putBoolean("syncDismiss",checked).apply()); column.addView(sync);
            column.addView(text("When iOS offers Clear or Dismiss. Other actions stay manual.",11,0xffadc0cf));
            button(probe.running ? "Pause connection" : "Connect to iPhone",false,() -> { if (probe.running) probe.stop(); else requestConnect(); render(); });
            if (probe.companionId() == -1) button("Background setup",false,this::enableBackground);
            button("Diagnostics",false,() -> navigate("diagnostics"));
            button("Back",false,() -> navigate("home"));
        } else {
            title("Diagnostics"); TextView diagnostic=text(probe.screen(),10,0xffadc0cf); diagnostic.setGravity(android.view.Gravity.START); column.addView(diagnostic);
            button("Back",false,() -> navigate("settings"));
        }
        scroll.post(() -> scroll.scrollTo(0,oldY));
    }
    private void details(long uid) {
        Probe probe=Probe.get(this); Probe.Entry entry=probe.entries.get(uid); if (entry==null) { Toast.makeText(this,"Notification no longer available",Toast.LENGTH_SHORT).show(); return; }
        String session=probe.notificationSession;
        LinearLayout content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(16),dp(8),dp(16),dp(12));
        ImageView icon=new ImageView(this); icon.setImageBitmap(AppIcons.bitmap(entry.app,probe.appName(entry.app))); content.addView(icon,new LinearLayout.LayoutParams(dp(32),dp(32)));
        TextView label=text(probe.appName(entry.app),13,0xff57dce8); content.addView(label);
        TextView body=text(entry.title+"\n\n"+entry.message,14,0xfff5f8fc); body.setPadding(0,dp(10),0,dp(10)); content.addView(body);
        ScrollView scroller=new ScrollView(this); scroller.addView(content);
        AlertDialog dialog=new AlertDialog.Builder(this).setView(scroller).setNegativeButton("Close",null).create();
        Button dismiss=new Button(this); dismiss.setText("Clear from watch"); dismiss.setAllCaps(false); content.addView(dismiss);
        dismiss.setOnClickListener(v -> { probe.dismiss(uid,session); dialog.dismiss(); });
        if (ReplyActivity.supports(entry.app)) {
            Button reply=new Button(this); reply.setText("Reply"); reply.setAllCaps(false);
            LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,dp(48)); layout.topMargin=dp(8); reply.setLayoutParams(layout); content.addView(reply);
            reply.setOnClickListener(v -> { startActivity(ReplyActivity.intent(this,uid,session)); dialog.dismiss(); });
        }
        String[] labels={entry.positive,entry.negative}; boolean[] flags={entry.hasPositive,entry.hasNegative};
        for (int i=0;i<2;i++) {
            if (!flags[i] || labels[i].isBlank()) continue;
            final int action=i; String name=labels[i];
            Button remote=new Button(this); remote.setText("iPhone · "+name); remote.setAllCaps(false);
            LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,dp(48)); layout.topMargin=dp(8); remote.setLayoutParams(layout); content.addView(remote);
            remote.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(name+" on iPhone?")
                .setMessage("Use the action offered by iOS for this notification.").setNegativeButton("Cancel",null)
                .setPositiveButton(name,(d,w) -> { probe.queueAction(uid,action,session); dialog.dismiss(); }).show());
        }
        dialog.setOnShowListener(d -> dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE)); dialog.show();
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,() -> {
            if (!page.equals("home")) navigate("home"); else finish();
        });
        scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(0xff050e15); scroll.setClipToPadding(false);
        column=new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL); column.setPadding(dp(26),dp(24),dp(26),dp(34)); scroll.addView(column); setContentView(scroll); render();
        if (getIntent().getBooleanExtra("connect",false)) requestConnect();
        long uid=getIntent().getLongExtra("detailUid",-1); String session=getIntent().getStringExtra("session");
        if (uid>=0 && Probe.get(this).notificationSession.equals(session)) scroll.post(() -> details(uid));
    }
    private void requestConnect() {
        ArrayList<String> needed=new ArrayList<>();
        for (String permission : new String[]{Manifest.permission.BLUETOOTH_ADVERTISE,Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.POST_NOTIFICATIONS})
            if (checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED) needed.add(permission);
        if (!needed.isEmpty()) requestPermissions(needed.toArray(new String[0]),1); else Probe.get(this).start();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)==PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED) Probe.get(this).start();
    }
    @Override protected void onResume() { super.onResume(); Probe.get(this).observer=refresh; Probe.get(this).resumeEnabled(); render(); }
    @Override protected void onPause() { Probe.get(this).observer=null; super.onPause(); }
    private void enableBackground() {
        Probe probe=Probe.get(this);
        if (probe.companionId()!=-1) { probe.reportPresence(true); Toast.makeText(this,"Background link enabled",Toast.LENGTH_SHORT).show(); }
        else new AlertDialog.Builder(this).setTitle("Background setup").setMessage("This watch needs a one-time ADB association. Connect wireless debugging to finish setup.").setPositiveButton("OK",null).show();
    }

    /** Process-owned probe deliberately uses no foreground service or status notification. */
    // The activity gates start on Nearby Devices permissions; the callback dispatcher handles revocation.
    @SuppressLint("MissingPermission")
    static final class Probe {
        static Probe instance;
        static Probe get(Context c) { if (instance == null) instance = new Probe(c.getApplicationContext()); return instance; }
        static final UUID ANCS = UUID.fromString("7905f431-b5ce-4e99-a40f-4b1e122d00d0");
        static final UUID SOURCE = UUID.fromString("9fbf120d-6301-42d9-8c58-25e699a21dbd");
        static final UUID DATA = UUID.fromString("22eac6e9-24d6-4bb5-be44-b36ace7c7bfb");
        static final UUID CONTROL = UUID.fromString("69d1d8f3-45e1-49a8-9821-9bbdfdaad9d9");
        static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        final Context context;
        final Handler handler = new Handler(Looper.getMainLooper()) {
            @Override public void dispatchMessage(Message message) {
                try { super.dispatchMessage(message); }
                catch (SecurityException revoked) { note("Bluetooth permission revoked. Stopping test."); stop(); }
            }
        };
        final ArrayDeque<String> log = new ArrayDeque<>();
        final LinkedHashMap<Long,Entry> entries = new LinkedHashMap<>();
        final LinkedHashSet<Long> dismissed = new LinkedHashSet<>();
        final Map<Long,String> sessionKeys = new HashMap<>();
        String notificationSession = UUID.randomUUID().toString();
        final ArrayDeque<Long> pending = new ArrayDeque<>();
        final ArrayDeque<PhoneAction> phoneActions = new ArrayDeque<>();
        PhoneAction activeAction;
        final Map<String,String> appNames = new HashMap<>();
        final LinkedHashSet<String> pendingApps = new LinkedHashSet<>();
        final NotificationManager notifications;
        BluetoothAdapter adapter;
        BluetoothGattServer server;
        BluetoothGatt gatt;
        BluetoothLeAdvertiser advertiser;
        BluetoothGattCharacteristic source, data, control;
        Runnable observer;
        boolean running, ready, discovering, controlWritePending, clientAutoConnect, clientConnected;
        int generation, discoveryAttempts, requestSequence;
        long active = -1;
        AncsParser parser;
        AncsAppParser appParser;
        String activeApp, connectionState = "Disconnected";
        int retryAttempt;
        Runnable reconnectTask;
        AdvertiseCallback advertising;
        final Runnable pruneTask = new Runnable() {
            @Override public void run() { pruneDismissals(); handler.postDelayed(this,6L*60*60*1000); }
        };
        Probe(Context context) {
            this.context = context; notifications = context.getSystemService(NotificationManager.class);
            pruneDismissals(); handler.postDelayed(pruneTask,6L*60*60*1000);
            notifications.createNotificationChannel(new NotificationChannel("iphone", "iPhone notifications", NotificationManager.IMPORTANCE_DEFAULT));
            NotificationChannel quiet = new NotificationChannel("iphone_quiet", "Silent iPhone notifications", NotificationManager.IMPORTANCE_LOW);
            quiet.setSound(null,null); quiet.enableVibration(false); notifications.createNotificationChannel(quiet);
            context.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent intent) {
                    int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,-1);
                    if (state == BluetoothAdapter.STATE_OFF && running) {
                        closeTransport(); connectionState = "Bluetooth off · waiting"; note("Bluetooth off; automatic recovery remains enabled.");
                    } else if (state == BluetoothAdapter.STATE_ON) resumeEnabled();
                }
            }, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), Context.RECEIVER_EXPORTED);
            note("Ready. Text stays in memory; diagnostics omit message content.");
        }
        void note(String value) {
            if (log.size() >= 24) log.removeFirst(); log.addLast(value);
            android.util.Log.i("WristLink", value); changed();
        }
        void changed() { if (observer != null) observer.run(); }
        String screen() {
            StringBuilder b = new StringBuilder();
            b.append(connectionState).append("\n\n");
            b.append("Background association: ").append(companionId() != -1 ? "approved" : "not enabled").append("\n");
            b.append("Companion setup: ").append(context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)).append("\n\n");
            b.append("Diagnostics\n"); for (String l : log) b.append(l).append('\n'); return b.toString();
        }
        int companionId() {
            if (Build.VERSION.SDK_INT < 33 || !context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)) return -1;
            CompanionDeviceManager manager = context.getSystemService(CompanionDeviceManager.class);
            if (manager == null) return -1;
            for (AssociationInfo association : manager.getMyAssociations()) if (!association.isSelfManaged()) return association.getId();
            return -1;
        }
        void reportPresence(boolean connected) {
            if (Build.VERSION.SDK_INT < 33) return;
            int id = companionId(); if (id == -1) return;
            try {
                CompanionDeviceManager manager = context.getSystemService(CompanionDeviceManager.class);
                if (Build.VERSION.SDK_INT >= 36) {
                    ObservingDevicePresenceRequest request = new ObservingDevicePresenceRequest.Builder().setAssociationId(id).build();
                    if (connected) manager.startObservingDevicePresence(request); else manager.stopObservingDevicePresence(request);
                } else {
                    for (AssociationInfo association : manager.getMyAssociations()) {
                        if (association.getId() == id && association.getDeviceMacAddress() != null) {
                            String address = association.getDeviceMacAddress().toString();
                            if (connected) manager.startObservingDevicePresence(address); else manager.stopObservingDevicePresence(address);
                        }
                    }
                }
                note(connected ? "Companion presence observation started." : "Companion presence observation stopped.");
            } catch (RuntimeException error) { note("Companion presence failed: " + error.getClass().getSimpleName()); }
        }
        void start() {
            if (running && server != null) {
                if (!ready && !clientConnected) {
                    BluetoothDevice peer=trustedPeer();
                    if (peer!=null && context.getSystemService(BluetoothManager.class).getConnectionState(peer,BluetoothProfile.GATT)==BluetoothProfile.STATE_CONNECTED)
                        adoptIncoming(peer,generation);
                }
                note("Link is already enabled."); return;
            }
            context.getSharedPreferences("link",0).edit().putBoolean("enabled",true).apply();
            running = true; int session = ++generation;
            try {
                BluetoothManager manager = context.getSystemService(BluetoothManager.class); adapter = manager.getAdapter();
                if (adapter == null || !adapter.isEnabled()) { connectionState = "Bluetooth off · waiting"; note("Waiting for Bluetooth."); return; }
                connectionState = "Connecting to iPhone…";
                reportPresence(true);
                note("API " + Build.VERSION.SDK_INT + "; advertising supported: " + adapter.isMultipleAdvertisementSupported());
                // A server callback supplies the identity of the central that connects to our advertiser.
                server = manager.openGattServer(context, new BluetoothGattServerCallback() {
                    @Override public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
                        handler.post(() -> {
                            if (generation == session && server != null) server.sendResponse(device, requestId,
                                offset == 0 ? BluetoothGatt.GATT_SUCCESS : BluetoothGatt.GATT_INVALID_OFFSET, offset,
                                offset == 0 ? new byte[]{1} : new byte[0]);
                        });
                    }
                    @Override public void onConnectionStateChange(BluetoothDevice device, int status, int state) {
                        handler.post(() -> {
                            if (!running || generation != session) return;
                            note("Incoming link: status " + status + ", state " + state);
                            if (status == 0 && state == BluetoothProfile.STATE_CONNECTED && !clientConnected) adoptIncoming(device,session);
                        });
                    }
                });
                if (server == null) { note("GATT server unavailable."); stop(); return; }
                // Diagnostic service makes the incoming connection observable. It carries no personal data.
                BluetoothGattService diagnostic = new BluetoothGattService(UUID.fromString("640f0001-912c-4b65-886e-8dcb1b500001"), BluetoothGattService.SERVICE_TYPE_PRIMARY);
                diagnostic.addCharacteristic(new BluetoothGattCharacteristic(UUID.fromString("640f0002-912c-4b65-886e-8dcb1b500001"), BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ));
                server.addService(diagnostic);
                advertiser = adapter.getBluetoothLeAdvertiser();
                if (advertiser == null) { note("BLE advertiser unavailable."); stop(); return; }
                advertise();
                BluetoothDevice trusted = trustedPeer();
                if (trusted != null && gatt == null) attach(trusted,session);
            } catch (RuntimeException e) { note("Start failed: " + e.getClass().getSimpleName()); stop(); }
        }
        void resumeEnabled() {
            if (context.getSharedPreferences("link",0).getBoolean("enabled",companionId() != -1)) start();
        }
        BluetoothDevice trustedPeer() {
            CompanionDeviceManager manager = context.getSystemService(CompanionDeviceManager.class);
            if (manager == null || adapter == null) return null;
            for (AssociationInfo association : manager.getMyAssociations()) {
                if (association.getDeviceMacAddress() != null) return adapter.getRemoteDevice(association.getDeviceMacAddress().toString().toUpperCase(Locale.ROOT));
            }
            return null;
        }
        void advertise() {
            if (!running || advertiser == null || advertising != null) return;
            int session = generation;
            advertising = new AdvertiseCallback() {
                @Override public void onStartSuccess(AdvertiseSettings settings) { handler.post(() -> { if (session == generation) note("Low-power reconnect advertising active."); }); }
                @Override public void onStartFailure(int code) { handler.post(() -> { if (session == generation) { advertising = null; note("Advertising failed: " + code); } }); }
            };
            advertiser.startAdvertising(new AdvertiseSettings.Builder().setConnectable(true).setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER).setTimeout(0).build(),
                new AdvertiseData.Builder().addServiceSolicitationUuid(new ParcelUuid(ANCS)).build(),
                new AdvertiseData.Builder().setIncludeDeviceName(true).build(),advertising);
        }
        void stopAdvertising() {
            if (advertiser != null && advertising != null) advertiser.stopAdvertising(advertising);
            advertising = null;
        }
        void attach(BluetoothDevice peer, int session) {
            attach(peer,session,peer.getBondState()==BluetoothDevice.BOND_BONDED);
        }
        void adoptIncoming(BluetoothDevice peer,int session) {
            BluetoothDevice trusted=trustedPeer();
            if (trusted!=null && !trusted.equals(peer)) { note("Ignored peer outside WristLink association."); return; }
            if (!running || generation!=session || clientConnected) return;
            note("Incoming connection available; replacing disconnected client.");
            if (reconnectTask!=null) { handler.removeCallbacks(reconnectTask); reconnectTask=null; }
            BluetoothGatt previous=gatt; gatt=null;
            // Unregister the stale client without disconnecting the newly arrived shared BLE link.
            if (previous!=null) previous.close();
            clearSession(); attach(peer,session,false);
        }
        void attach(BluetoothDevice peer, int session, boolean autoConnect) {
            BluetoothDevice trusted = trustedPeer();
            if (trusted != null && !trusted.equals(peer)) { note("Ignored peer outside WristLink association."); return; }
            note("Attaching GATT client; mode="+(autoConnect ? "auto" : "incoming/direct")+"; bond="+peer.getBondState());
            clientConnected=false;
            clientAutoConnect = autoConnect;
            gatt = peer.connectGatt(context, clientAutoConnect, new BluetoothGattCallback() {
                @Override public void onConnectionStateChange(BluetoothGatt connection, int status, int state) {
                    handler.post(() -> {
                        if (session != generation || gatt != connection) return;
                        note("Client link: status " + status + ", state " + state);
                        if (status == 0 && state == BluetoothProfile.STATE_CONNECTED) {
                            clientConnected=true;
                            clearSession(); stopAdvertising(); connectionState = "Restoring notifications…";
                            watchSetup(connection,session);
                            reportPresence(true);
                            if (peer.getBondState() == BluetoothDevice.BOND_NONE) note("Bond requested: " + peer.createBond());
                            discover(connection);
                        } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                            clientConnected=false;
                            clearSession(); connectionState = "Reconnecting to iPhone…";
                            advertise(); note("Disconnected; waiting for automatic reconnect.");
                            if (status == 133 || !clientAutoConnect) reconnectClient();
                        }
                    });
                }
                @Override public void onServicesDiscovered(BluetoothGatt connection, int status) {
                    handler.post(() -> {
                        if (session != generation || gatt != connection) return;
                        discovering = false; note("Service discovery: " + status);
                        BluetoothGattService service = connection.getService(ANCS);
                        if (status != 0 || service == null) {
                            if (discoveryAttempts >= 5) { note("ANCS absent; retrying the link."); reconnectClient(); return; }
                            note("ANCS absent. Waiting for bond/service change; retry discovery in 3s.");
                            handler.postDelayed(() -> { if (gatt == connection && running) discover(connection); },3000); return;
                        }
                        source = service.getCharacteristic(SOURCE); data = service.getCharacteristic(DATA); control = service.getCharacteristic(CONTROL);
                        if (source == null || data == null || control == null) { note("Required text characteristics missing."); return; }
                        subscribe(data);
                    });
                }
                @Override public void onServiceChanged(BluetoothGatt connection) {
                    handler.post(() -> { if (session == generation && gatt == connection) { clearSession(); discover(connection); } });
                }
                @Override public void onDescriptorWrite(BluetoothGatt connection, BluetoothGattDescriptor descriptor, int status) {
                    handler.post(() -> {
                        if (session != generation || gatt != connection) return;
                        note("Subscription " + (descriptor.getCharacteristic().getUuid().equals(DATA) ? "data" : "events") + ": " + status);
                        if (status != 0) { note("Subscription rejected; check iPhone notification sharing. Retrying link."); reconnectClient(); return; }
                        if (descriptor.getCharacteristic().getUuid().equals(DATA)) subscribe(source);
                        else { ready = true; retryAttempt = 0; connectionState = "Connected · notifications ready"; note("ANCS ready."); next(); }
                    });
                }
                @Override public void onCharacteristicWrite(BluetoothGatt connection, BluetoothGattCharacteristic characteristic, int status) {
                    handler.post(() -> {
                        if (session != generation || gatt != connection) return;
                        controlWritePending = false;
                        if (activeAction != null) {
                            note(status == 0 ? "iOS action request accepted; waiting for phone state." : "iOS action request rejected: " + status);
                            Toast.makeText(context,status==0 ? "Action requested on iPhone" : "iPhone rejected the action",Toast.LENGTH_SHORT).show();
                            activeAction = null; next(); return;
                        }
                        if (status != 0) {
                            if (appParser != null) { appNames.put(activeApp,appName(activeApp)); appParser = null; activeApp = null; next(); }
                            else { ready = false; failRequest("Attribute request failed: " + status); reconnectClient(); }
                        }
                        else next();
                    });
                }
                @Override public void onCharacteristicChanged(BluetoothGatt connection, BluetoothGattCharacteristic characteristic, byte[] value) {
                    byte[] copy = value.clone(); handler.post(() -> { if (session == generation && gatt == connection) receive(characteristic.getUuid(), copy); });
                }
                @Override public void onCharacteristicChanged(BluetoothGatt connection, BluetoothGattCharacteristic characteristic) {
                    if (Build.VERSION.SDK_INT < 33) onCharacteristicChanged(connection, characteristic, characteristic.getValue());
                }
            }, BluetoothDevice.TRANSPORT_LE);
            BluetoothGatt attempt=gatt;
            if (attempt==null) { note("Client registration returned no handle."); reconnectClient(); return; }
            watchSetup(attempt,session);
        }
        void watchSetup(BluetoothGatt attempt,int session) {
            String epoch=notificationSession;
            handler.postDelayed(() -> {
                if (running && generation==session && notificationSession.equals(epoch) && gatt==attempt && !ready) {
                    note("ANCS setup watchdog expired; rebuilding client."); reconnectClient();
                }
            },45000);
        }
        void discover(BluetoothGatt connection) {
            if (!discovering) { discoveryAttempts++; discovering = connection.discoverServices(); note("Discover requested: " + discovering); }
        }
        void subscribe(BluetoothGattCharacteristic characteristic) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD);
            if (descriptor == null || !gatt.setCharacteristicNotification(characteristic,true)) { note("Cannot enable characteristic notifications."); reconnectClient(); return; }
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            if (!gatt.writeDescriptor(descriptor)) { note("Subscription could not start."); reconnectClient(); }
        }
        void receive(UUID characteristic, byte[] value) {
            if (characteristic.equals(SOURCE)) {
                if (value.length != 8) { note("Malformed event length."); return; }
                int event = value[0] & 255, flags = value[1] & 255; long uid = AncsParser.unsigned32(value,4);
                if (event == 2) {
                    String key=sessionKeys.remove(uid); if (key!=null && !key.isEmpty()) context.getSharedPreferences("dismissals",0).edit().remove(key).apply();
                    phoneActions.removeIf(action -> action.uid==uid); dismissed.remove(uid); entries.remove(uid); pending.remove(uid); notifications.cancel("ancs", (int)uid); changed(); return;
                }
                if (event > 1) return;
                if (dismissed.contains(uid)) {
                    if (event!=1) return;
                    // iOS may reuse a grouped notification's UID for new content.
                    dismissed.remove(uid);
                }
                Entry entry = entries.get(uid);
                if (entry == null) { entry = new Entry(); entries.put(uid,entry); }
                entry.silent = (flags & (1 | 4)) != 0;
                entry.category=value[2] & 255; entry.hasPositive=(flags & 8)!=0; entry.hasNegative=(flags & 16)!=0;
                if (!pending.contains(uid)) pending.add(uid);
                while (entries.size() > 40) { long old = entries.keySet().iterator().next(); entries.remove(old); sessionKeys.remove(old); pending.remove(old); notifications.cancel("ancs",(int)old); }
                note("Notification event " + event + "; cached " + entries.size()); next();
            } else if (characteristic.equals(DATA) && appParser != null) {
                try {
                    String name = appParser.feed(value);
                    if (name == null) return;
                    String app = activeApp;
                    appNames.put(app,name.isBlank() ? appName(app) : name);
                    appParser = null; activeApp = null;
                    for (Map.Entry<Long,Entry> item : entries.entrySet()) if (item.getValue().app.equals(app)) show(item.getKey(),item.getValue(),true);
                    changed(); next();
                } catch (IllegalArgumentException malformed) { note("Malformed app-name response; recovering link."); reconnectClient(); }
            } else if (characteristic.equals(DATA) && parser != null) {
                try {
                    Map<Integer,String> attrs = parser.feed(value);
                    if (attrs == null) return;
                    Entry e = entries.get(active);
                    if (e != null) {
                        e.app=attrs.get(0); e.title=attrs.get(1); e.message=attrs.get(3); e.date=attrs.get(5); e.positive=attrs.get(6); e.negative=attrs.get(7);
                        e.key=DismissalKey.of(e.app,e.title,e.message,e.date);
                        sessionKeys.put(active,e.key);
                        if (wasDismissed(e.key)) { markDismissed(active); entries.remove(active); }
                        else show(active,e,false);
                        if (!e.app.isBlank() && !appNames.containsKey(e.app)) pendingApps.add(e.app);
                    }
                    parser = null; active = -1; changed(); next();
                } catch (IllegalArgumentException e) { ready = false; failRequest("Malformed attribute response; recovering link."); reconnectClient(); }
            }
        }
        void next() {
            if (!ready || active != -1 || appParser != null || activeAction != null || controlWritePending || gatt == null) return;
            byte[] command;
            if (!phoneActions.isEmpty()) {
                activeAction=phoneActions.removeFirst();
                if (!notificationSession.equals(activeAction.session)) { activeAction=null; next(); return; }
                long uid=activeAction.uid;
                command=new byte[]{2,(byte)uid,(byte)(uid>>8),(byte)(uid>>16),(byte)(uid>>24),(byte)activeAction.action};
            } else if (!pending.isEmpty()) {
                active = pending.removeFirst(); parser = new AncsParser(active,0,1,3,5,6,7);
                long uid = active;
                command = new byte[]{0,(byte)uid,(byte)(uid>>8),(byte)(uid>>16),(byte)(uid>>24),0,1,120,0,3,(byte)244,1,5,6,7};
            } else if (!pendingApps.isEmpty()) {
                activeApp = pendingApps.iterator().next(); pendingApps.remove(activeApp);
                appParser = new AncsAppParser(activeApp);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(); bytes.write(1); bytes.writeBytes(activeApp.getBytes(StandardCharsets.UTF_8)); bytes.write(0); bytes.write(0);
                command = bytes.toByteArray();
            } else return;
            int session = generation, sequence = ++requestSequence;
            control.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT); control.setValue(command);
            controlWritePending = true;
            if (!gatt.writeCharacteristic(control)) { controlWritePending = false; ready = false; failRequest("Attribute write could not start."); reconnectClient(); return; }
            handler.postDelayed(() -> { if (session == generation && sequence == requestSequence && (parser != null || appParser != null || controlWritePending)) {
                ready = false; failRequest("Attribute timeout; recovering link."); reconnectClient();
            } },10000);
        }
        void failRequest(String reason) { note(reason); parser = null; active = -1; }
        String appName(String app) {
            if (app == null || app.isBlank()) return "iPhone";
            String name = appNames.get(app); if (name != null) return name;
            return switch(app.toLowerCase(Locale.ROOT)) {
                case "com.apple.mobilesms" -> "Messages";
                case "com.apple.mobilephone" -> "Phone";
                case "com.apple.mobilemail" -> "Mail";
                case "com.toyopagroup.picaboo" -> "Snapchat";
                case "com.yourcompany.ppclient" -> "PayPal";
                default -> "iPhone app";
            };
        }
        void show(long uid, Entry e, boolean refreshOnly) {
            if (dismissed.contains(uid)) return;
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
            Intent intent = new Intent(context,MainActivity.class).setData(android.net.Uri.parse("wristlink://detail/"+notificationSession+"/"+uid))
                .putExtra("detailUid",uid).putExtra("session",notificationSession);
            PendingIntent open = PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Intent dismiss = new Intent(context,DismissReceiver.class).setData(android.net.Uri.parse("wristlink://dismiss/"+notificationSession+"/"+uid))
                .putExtra("uid",uid).putExtra("session",notificationSession);
            PendingIntent deleted = PendingIntent.getBroadcast(context,0,dismiss,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder builder = new Notification.Builder(context,e.silent || refreshOnly ? "iphone_quiet" : "iphone").setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(AppIcons.bitmap(e.app,appName(e.app)))
                .setContentTitle(e.title.isEmpty() ? appName(e.app) : e.title).setContentText(e.message)
                .setStyle(new Notification.BigTextStyle().bigText(e.message)).setSubText(appName(e.app))
                .setContentIntent(open).setOnlyAlertOnce(true)
                .setGroup("ancs."+notificationSession+"."+uid)
                .setDeleteIntent(deleted)
                .setVisibility(Notification.VISIBILITY_PRIVATE);
            builder.addAction(new Notification.Action.Builder(null,"Open & actions",open).build());
            if (ReplyActivity.supports(e.app)) {
                PendingIntent reply=PendingIntent.getActivity(context,0,ReplyActivity.intent(context,uid,notificationSession),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                builder.addAction(new Notification.Action.Builder(null,"Reply",reply).build());
            }
            notifications.notify("ancs",(int)uid,builder.build());
            note(refreshOnly ? "App label refreshed silently." : "Notification posted; silent=" + e.silent);
        }
        void dismiss(long uid, String session) {
            if (!notificationSession.equals(session) || uid < 0) return;
            Entry e=entries.get(uid);
            if (e!=null) {
                rememberDismissed(e.key);
                if (syncDismiss() && ActionPolicy.canAutoDismiss(e.category,e.hasNegative,e.negative)) queueAction(uid,1,session);
            }
            markDismissed(uid); entries.remove(uid); pending.remove(uid); notifications.cancel("ancs",(int)uid); changed();
            note("Notification dismissed locally.");
        }
        void markDismissed(long uid) {
            dismissed.add(uid);
            while (dismissed.size()>256) { long oldest=dismissed.iterator().next(); dismissed.remove(oldest); sessionKeys.remove(oldest); }
        }
        boolean syncDismiss() { return context.getSharedPreferences("link",0).getBoolean("syncDismiss",false); }
        boolean wasDismissed(String key) {
            if (key.isEmpty()) return false;
            SharedPreferences prefs=context.getSharedPreferences("dismissals",0);
            long time=prefs.getLong(key,0), age=System.currentTimeMillis()-time;
            if (time>0 && age>=0 && age<7L*24*60*60*1000) return true;
            if (time>0) prefs.edit().remove(key).apply(); return false;
        }
        void pruneDismissals() {
            SharedPreferences prefs=context.getSharedPreferences("dismissals",0); SharedPreferences.Editor edit=prefs.edit();
            ArrayList<Map.Entry<String,?>> values=new ArrayList<>(prefs.getAll().entrySet());
            values.sort(Comparator.comparingLong(item -> item.getValue() instanceof Long ? (Long)item.getValue() : 0));
            long now=System.currentTimeMillis(); int remaining=values.size();
            for (Map.Entry<String,?> item : values) {
                long time=item.getValue() instanceof Long ? (Long)item.getValue() : 0;
                if (time<=0 || time>now || now-time>=7L*24*60*60*1000 || remaining>256) { edit.remove(item.getKey()); remaining--; }
            }
            edit.apply();
        }
        void rememberDismissed(String key) {
            if (key.isEmpty()) return;
            SharedPreferences prefs=context.getSharedPreferences("dismissals",0);
            SharedPreferences.Editor edit=prefs.edit(); long now=System.currentTimeMillis();
            ArrayList<Map.Entry<String,?>> values=new ArrayList<>(prefs.getAll().entrySet());
            values.sort(Comparator.comparingLong(item -> item.getValue() instanceof Long ? (Long)item.getValue() : 0));
            int remaining=values.size();
            for (Map.Entry<String,?> item : values) {
                long time=item.getValue() instanceof Long ? (Long)item.getValue() : 0;
                if (now-time>7L*24*60*60*1000 || remaining>=256) { edit.remove(item.getKey()); remaining--; }
            }
            edit.putLong(key,now).apply();
        }
        void queueAction(long uid,int action,String session) {
            Entry e=entries.get(uid);
            if (!ready || !notificationSession.equals(session) || e==null || action<0 || action>1 || (action==0 ? !e.hasPositive || e.positive.isBlank() : !e.hasNegative || e.negative.isBlank())) {
                note("iOS action unavailable for this notification/session."); return;
            }
            phoneActions.addLast(new PhoneAction(uid,action,session)); next();
        }
        void clearSession() { pruneDismissals(); sessionKeys.clear(); phoneActions.clear(); activeAction=null; notificationSession = UUID.randomUUID().toString(); dismissed.clear(); requestSequence++; ready = false; discovering = false; controlWritePending = false; discoveryAttempts = 0; parser = null; appParser = null; activeApp = null; appNames.clear(); pendingApps.clear(); active = -1; pending.clear(); entries.clear(); notifications.cancelAll(); changed(); }
        void reconnectClient() {
            if (!running || reconnectTask != null) return;
            clientConnected=false;
            clearSession();
            BluetoothGatt previous = gatt; gatt = null;
            if (previous != null) { previous.disconnect(); previous.close(); }
            connectionState = "Reconnecting to iPhone…"; advertise();
            long delay = Math.min(60000L,2000L << Math.min(retryAttempt++,5)); int session = generation;
            note("Link recovery scheduled in " + delay/1000 + "s.");
            reconnectTask = () -> {
                reconnectTask = null;
                if (running && session == generation && gatt == null) {
                    BluetoothDevice peer = trustedPeer(); if (peer != null) attach(peer,session);
                }
            };
            handler.postDelayed(reconnectTask,delay);
        }
        void closeTransport() {
            generation++; handler.removeCallbacksAndMessages(null); reconnectTask = null;
            handler.postDelayed(pruneTask,6L*60*60*1000);
            try { stopAdvertising(); } catch (RuntimeException ignored) {}
            try { if (gatt != null) { gatt.disconnect(); gatt.close(); } } catch (RuntimeException ignored) {}
            try { if (server != null) server.close(); } catch (RuntimeException ignored) {}
            gatt = null; clientConnected=false; server = null; advertiser = null; advertising = null; clearSession();
        }
        void stop() {
            context.getSharedPreferences("link",0).edit().putBoolean("enabled",false).apply();
            running = false;
            reportPresence(false);
            closeTransport(); connectionState = "Stopped"; note("Stopped; session content cleared. Bonds unchanged.");
        }
        static final class PhoneAction { final long uid; final int action; final String session; PhoneAction(long uid,int action,String session) { this.uid=uid; this.action=action; this.session=session; } }
        static final class Entry { String app="", title="Loading…", message="", date="", positive="", negative="", key=""; boolean silent,hasPositive,hasNegative; int category; }
    }
}
