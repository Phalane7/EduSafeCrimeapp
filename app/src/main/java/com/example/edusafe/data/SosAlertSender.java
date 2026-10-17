package com.example.edusafe.data;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SosAlertSender
 * ----------------------------------------------------------------------
 * Does the actual work behind "HOLD FOR HELP": gets the current location,
 * then sends a real SMS (via SmsManager) to every contact in
 * ContactsRepository, in priority order.
 *
 * An alert must never be held up by GPS. If no location arrives within
 * LOCATION_WAIT_MS, the texts go out anyway, without a location.
 * The texts are only ever sent once per sendAlert() call.
 *
 * Needs the SEND_SMS runtime permission; without it nothing is sent and
 * the callback gets an empty list, so the caller can tell the user.
 * ----------------------------------------------------------------------
 */
public class SosAlertSender {

    public interface AlertCallback {
        /** Called once, on the main thread, with the contacts that were actually texted (possibly none). */
        void onAlertSent(List<EmergencyContact> notifiedContacts, boolean locationIncluded);
    }

    /** How long to wait for a location before sending without one. */
    private static final long LOCATION_WAIT_MS = 3000L;

    private final Context context;
    private final ContactsRepository contactsRepository;
    private final LocationHelper locationHelper;

    public SosAlertSender(Context context) {
        this.context = context.getApplicationContext();
        this.contactsRepository = new ContactsRepository(context);
        this.locationHelper = new LocationHelper(context);
    }

    public static boolean hasSmsPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public void sendAlert(AlertCallback callback) {
        Handler handler = new Handler(Looper.getMainLooper());
        AtomicBoolean alreadySent = new AtomicBoolean(false);

        // If no location turns up in time, send without one.
        Runnable stopWaiting = () -> deliver(null, alreadySent, callback);
        handler.postDelayed(stopWaiting, LOCATION_WAIT_MS);

        locationHelper.getCurrentLocation(location -> {
            handler.removeCallbacks(stopWaiting);
            deliver(location, alreadySent, callback);
        });
    }

    private void deliver(Location location, AtomicBoolean alreadySent, AlertCallback callback) {
        if (!alreadySent.compareAndSet(false, true)) return; // a late location must not trigger a second send
        List<EmergencyContact> notified = sendToAllContacts(buildMessage(location));
        callback.onAlertSent(notified, location != null);
    }

    private String buildMessage(Location location) {
        StringBuilder sb = new StringBuilder("EMERGENCY: I need help. This is an automated alert from EduSafe.");
        if (location != null) {
            sb.append(" My location: ").append(LocationHelper.toMapsLink(location));
        } else {
            sb.append(" (Location unavailable)");
        }
        return sb.toString();
    }

    private List<EmergencyContact> sendToAllContacts(String message) {
        List<EmergencyContact> notified = new ArrayList<>();
        if (!hasSmsPermission(context)) {
            return notified;
        }

        SmsManager smsManager = SmsManager.getDefault();
        for (EmergencyContact contact : contactsRepository.getContacts()) {
            try {
                // divideMessage handles messages longer than one SMS segment safely
                ArrayList<String> parts = smsManager.divideMessage(message);
                smsManager.sendMultipartTextMessage(contact.phoneNumber, null, parts, null, null);
                notified.add(contact);
            } catch (Exception e) {
                // Keep going: one bad number must not stop the others from being alerted.
            }
        }
        return notified;
    }
}
