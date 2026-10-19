package com.example.edusafe.data;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;

import androidx.core.content.ContextCompat;

import com.example.edusafe.DatabaseHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class SosAlertSender {

    public interface AlertCallback {
        void onAlertSent(
                List<EmergencyContact> notifiedContacts,
                boolean locationIncluded
        );
    }

    private static final long LOCATION_WAIT_MS = 3000L;

    private final Context context;
    private final DatabaseHelper db;
    private final LocationHelper locationHelper;

    public SosAlertSender(Context context) {

        this.context = context.getApplicationContext();

        // Use the same database as TrustedContactsFragment.
        this.db = DatabaseHelper.get(this.context);

        this.locationHelper = new LocationHelper(this.context);
    }

    // ----------------------------------------------------
    // SMS PERMISSION
    // ----------------------------------------------------

    public static boolean hasSmsPermission(Context context) {

        return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED;
    }

    // ----------------------------------------------------
    // START SOS ALERT
    // ----------------------------------------------------

    public void sendAlert(AlertCallback callback) {

        Handler handler = new Handler(Looper.getMainLooper());

        AtomicBoolean alreadySent = new AtomicBoolean(false);

        // If GPS takes too long, send without location.
        Runnable stopWaiting = () ->
                deliver(null, alreadySent, callback);

        handler.postDelayed(stopWaiting, LOCATION_WAIT_MS);

        try {

            locationHelper.getCurrentLocation(location -> {

                handler.removeCallbacks(stopWaiting);

                deliver(location, alreadySent, callback);
            });

        } catch (Exception e) {

            handler.removeCallbacks(stopWaiting);

            deliver(null, alreadySent, callback);
        }
    }

    // ----------------------------------------------------
    // SEND ALERT ONCE
    // ----------------------------------------------------

    private void deliver(
            Location location,
            AtomicBoolean alreadySent,
            AlertCallback callback) {

        // Prevent duplicate SMS messages.
        if (!alreadySent.compareAndSet(false, true)) {
            return;
        }

        String message = buildMessage(location);

        // These are SMS submissions, not confirmed deliveries.
        List<EmergencyContact> submitted =
                sendToAllContacts(message);

        new Handler(Looper.getMainLooper()).post(() -> {

            if (callback != null) {
                callback.onAlertSent(
                        submitted,
                        location != null && !submitted.isEmpty()
                );
            }
        });
    }

    // ----------------------------------------------------
    // BUILD SOS MESSAGE
    // ----------------------------------------------------

    private String buildMessage(Location location) {

        StringBuilder message = new StringBuilder();

        message.append(
                "EMERGENCY: I need help. "
                        + "This is an automated SOS alert from EduSafe."
        );

        if (location != null) {

            message.append(" My location: ");

            message.append(
                    LocationHelper.toMapsLink(location)
            );

        } else {

            message.append(
                    " Location is currently unavailable."
            );
        }

        return message.toString();
    }

    // ----------------------------------------------------
    // GET SAVED CONTACTS AND SEND SMS
    // ----------------------------------------------------

    private List<EmergencyContact> sendToAllContacts(
            String message) {

        List<EmergencyContact> submitted =
                new ArrayList<>();

        if (!hasSmsPermission(context)) {
            return submitted;
        }

        List<DatabaseHelper.Contact> savedContacts;

        try {

            // Read directly from Trusted Contacts database.
            savedContacts = db.getContacts();

        } catch (Exception e) {

            return submitted;
        }

        SmsManager smsManager = SmsManager.getDefault();

        for (DatabaseHelper.Contact contact : savedContacts) {

            if (contact.phone == null
                    || contact.phone.trim().isEmpty()) {
                continue;
            }

            String phone = contact.phone.trim();

            try {

                ArrayList<String> parts =
                        smsManager.divideMessage(message);

                if (parts == null || parts.isEmpty()) {
                    continue;
                }

                smsManager.sendMultipartTextMessage(
                        phone,
                        null,
                        parts,
                        null,
                        null
                );

                // This only confirms that Android accepted
                // the send request without throwing an error.
                // It does NOT confirm SMS delivery.
                submitted.add(
                        new EmergencyContact(
                                contact.name,
                                phone,
                                contact.priority
                        )
                );

            } catch (Exception e) {

                // Continue attempting other contacts.
            }
        }

        return submitted;
    }
}

