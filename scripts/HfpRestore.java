import android.bluetooth.*;
import android.content.Context;
import android.os.*;
import java.lang.reflect.*;
import java.util.*;

/** Requests the existing bonded iPhone's native Hands-Free profile as the ADB shell user. */
public final class HfpRestore {
    private static final UUID HFP_GATEWAY = UUID.fromString("0000111f-0000-1000-8000-00805f9b34fb");
    private static final UUID MESSAGE_SERVER = UUID.fromString("00001132-0000-1000-8000-00805f9b34fb");
    private static final int HEADSET_CLIENT = 16;
    private static final int POLICY_ALLOWED = 100;

    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "connect" : args[0];
        if (!Set.of("connect", "enable", "status").contains(mode)) throw new IllegalArgumentException("Unknown mode");
        Looper.prepareMainLooper();
        Class<?> initializer = Class.forName("android.bluetooth.BluetoothFrameworkInitializer");
        Class<?> manager = Class.forName("android.os.BluetoothServiceManager");
        initializer.getMethod("setBluetoothServiceManager", manager).invoke(null, manager.getConstructor().newInstance());
        Class<?> threadType = Class.forName("android.app.ActivityThread");
        Object thread = threadType.getMethod("systemMain").invoke(null);
        Context system = (Context) threadType.getMethod("getSystemContext").invoke(thread);
        Context shell = system.createPackageContext("com.android.shell", Context.CONTEXT_IGNORE_SECURITY);
        BluetoothAdapter adapter = shell.getSystemService(BluetoothManager.class).getAdapter();
        List<BluetoothDevice> matches = new ArrayList<>();
        for (BluetoothDevice peer : adapter.getBondedDevices()) {
            ParcelUuid[] uuids = peer.getUuids();
            if (uuids == null) continue;
            List<ParcelUuid> services = Arrays.asList(uuids);
            if (services.contains(new ParcelUuid(HFP_GATEWAY)) && services.contains(new ParcelUuid(MESSAGE_SERVER))) matches.add(peer);
        }
        if (matches.size() != 1) throw new IllegalStateException("Expected one bonded phone with calling and messaging");
        BluetoothDevice target = matches.get(0);
        Handler handler = new Handler(Looper.getMainLooper());
        boolean requested = adapter.getProfileProxy(shell, new BluetoothProfile.ServiceListener() {
            public void onServiceConnected(int id, BluetoothProfile profile) {
                try {
                    System.out.println("Initial Hands-Free state: " + profile.getConnectionState(target));
                    Method readPolicy = profile.getClass().getMethod("getConnectionPolicy", BluetoothDevice.class);
                    int previousPolicy = (int) readPolicy.invoke(profile, target);
                    System.out.println("Saved Hands-Free policy: " + previousPolicy);
                    if (mode.equals("enable") && previousPolicy != POLICY_ALLOWED) {
                        boolean saved = (boolean) profile.getClass().getMethod("setConnectionPolicy", BluetoothDevice.class, int.class).invoke(profile, target, POLICY_ALLOWED);
                        int updated = (int) readPolicy.invoke(profile, target);
                        System.out.println("Hands-Free preference saved: " + saved + ", policy: " + updated);
                        if (!saved || updated != POLICY_ALLOWED) System.exit(6);
                    } else if (mode.equals("connect")) {
                        boolean started = (boolean) profile.getClass().getMethod("connect", BluetoothDevice.class).invoke(profile, target);
                        System.out.println("Hands-Free connect requested: " + started);
                    }
                    handler.postDelayed(() -> {
                        int state = profile.getConnectionState(target);
                        System.out.println("Final Hands-Free state: " + state);
                        adapter.closeProfileProxy(HEADSET_CLIENT, profile);
                        System.exit(state == BluetoothProfile.STATE_CONNECTED ? 0 : 3);
                    }, mode.equals("status") ? 0 : 5000);
                } catch (Exception error) {
                    Throwable cause = error instanceof InvocationTargetException ? error.getCause() : error;
                    System.out.println("Hands-Free request failed: " + cause.getClass().getSimpleName());
                    System.exit(4);
                }
            }
            public void onServiceDisconnected(int id) {}
        }, HEADSET_CLIENT);
        if (!requested) throw new IllegalStateException("Hands-Free profile proxy unavailable");
        handler.postDelayed(() -> { System.out.println("Hands-Free service response timed out"); System.exit(5); }, 15000);
        Looper.loop();
    }
}
