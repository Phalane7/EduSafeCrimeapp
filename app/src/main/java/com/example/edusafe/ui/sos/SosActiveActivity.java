package com.example.edusafe.ui.sos;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.edusafe.R;
import com.example.edusafe.data.ContactsRepository;
import com.example.edusafe.data.EmergencyContact;
import com.example.edusafe.data.SosAlertSender;
import com.example.edusafe.ui.common.SystemBars;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SosActiveActivity  (screen 2 of 3: SOS Active)
 * ----------------------------------------------------------------------
 * Opens the instant the 4-second hold completes. It does NOT wait for
 * anything:
 *
 *   1. the page, a card for every contact and the location card appear
 *      straight away, each in a "sending" state,
 *   2. the elapsed-time counter starts,
 *   3. the alert is sent in the background (SosAlertSender); when it
 *      finishes, each card shows "Alert sent" with a green tick (or a red
 *      warning if that contact couldn't be texted), and the location card
 *      says whether the location went out.
 *
 * "Call Campus Security" always works, whatever happened with the texts.
 * ----------------------------------------------------------------------
 */
public class SosActiveActivity extends AppCompatActivity {

    /** Opens the phone's dialer with this number ready to call. */
    private static final String CAMPUS_SECURITY_NUMBER = "0111112222";

    /** Shown on the location card. Replace with a real place name (reverse geocoding) when the group wants one. */
    private static final String LOCATION_LABEL = "Eduvos Midrand Campus";

    private static final int GREEN = Color.parseColor("#1F8A4C");
    private static final int RED = Color.parseColor("#B50218");
    private static final int GREY = Color.parseColor("#6B6B6B");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int elapsedSeconds = 0;
    private TextView timerText;
    private TextView statusSubtitle;

    private final List<EmergencyContact> contacts = new ArrayList<>();
    private final List<ImageView> statusIcons = new ArrayList<>();
    private final List<TextView> statusLabels = new ArrayList<>();

    private TextView locationTitle, locationSubtitle;
    private ImageView locationStatus;

    private final Runnable timerTick = new Runnable() {
        @Override
        public void run() {
            elapsedSeconds++;
            updateTimerText();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sos_active);
        SystemBars.applyPadded(this, findViewById(R.id.rootLayout), false);

        timerText = findViewById(R.id.timerText);
        statusSubtitle = findViewById(R.id.statusSubtitle);
        locationTitle = findViewById(R.id.locationTitle);
        locationSubtitle = findViewById(R.id.locationSubtitle);
        locationStatus = findViewById(R.id.locationStatus);

        updateTimerText();
        handler.postDelayed(timerTick, 1000);
        pulseTheLiveDot();

        showContacts();
        markLocationSending();
        setUpButtons();
        sendAlert();
    }

    /** A soft blink on the dot next to "SOS ACTIVE", so it's clear the alert is live. */
    private void pulseTheLiveDot() {
        AlphaAnimation pulse = new AlphaAnimation(1f, 0.2f);
        pulse.setDuration(700);
        pulse.setRepeatMode(Animation.REVERSE);
        pulse.setRepeatCount(Animation.INFINITE);
        findViewById(R.id.liveDot).startAnimation(pulse);
    }

    // ---------------------------------------------------------------
    // Timer
    // ---------------------------------------------------------------

    private void updateTimerText() {
        int minutes = elapsedSeconds / 60;
        int seconds = elapsedSeconds % 60;
        timerText.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
    }

    // ---------------------------------------------------------------
    // Contact cards
    // ---------------------------------------------------------------

    private void showContacts() {
        LinearLayout container = findViewById(R.id.contactsList);
        LayoutInflater inflater = LayoutInflater.from(this);

        contacts.addAll(new ContactsRepository(this).getContacts());
        for (EmergencyContact contact : contacts) {
            View row = inflater.inflate(R.layout.item_alert_contact, container, false);

            ((TextView) row.findViewById(R.id.contactAvatar)).setText(initialsFor(contact.name));
            ((TextView) row.findViewById(R.id.contactName)).setText(contact.name);
            ((TextView) row.findViewById(R.id.contactDetails))
                    .setText("Priority " + contact.priority + " \u00b7 " + formatPhone(contact.phoneNumber));

            ImageView icon = row.findViewById(R.id.contactStatus);
            TextView label = row.findViewById(R.id.contactStatusText);
            markSending(icon, label);
            statusIcons.add(icon);
            statusLabels.add(label);

            container.addView(row);
        }
    }

    /** "CS" for Campus Security, "PG" for Parent / Guardian, "TF" for Trusted Friend. */
    private String initialsFor(String name) {
        StringBuilder initials = new StringBuilder();
        for (String word : name.trim().split("\\s+")) {
            if (!word.isEmpty() && Character.isLetterOrDigit(word.charAt(0)) && initials.length() < 2) {
                initials.append(word.charAt(0));
            }
        }
        if (initials.length() == 0) return "?";
        return initials.toString().toUpperCase(Locale.getDefault());
    }

    /** 0111112222 becomes 011 111 2222. */
    private String formatPhone(String number) {
        if (number.length() == 10) {
            return number.substring(0, 3) + " " + number.substring(3, 6) + " " + number.substring(6);
        }
        return number;
    }

    private void markSending(ImageView icon, TextView label) {
        icon.setImageResource(R.drawable.ic_check);
        icon.setAlpha(0.25f);
        label.setText("Sending\u2026");
        label.setTextColor(GREY);
    }

    private void markSent(ImageView icon, TextView label) {
        icon.setImageResource(R.drawable.ic_check);
        icon.setAlpha(1f);
        label.setText("Alert sent");
        label.setTextColor(GREEN);
    }

    private void markFailed(ImageView icon, TextView label) {
        icon.setImageResource(R.drawable.ic_alert_circle);
        icon.setAlpha(1f);
        label.setText("Couldn't send");
        label.setTextColor(RED);
    }

    // ---------------------------------------------------------------
    // Location card
    // ---------------------------------------------------------------

    private void markLocationSending() {
        locationTitle.setText("Sharing your location\u2026");
        locationSubtitle.setText(LOCATION_LABEL);
        locationStatus.setImageResource(R.drawable.ic_check);
        locationStatus.setAlpha(0.25f);
    }

    private void markLocationShared() {
        locationTitle.setText("Live location shared");
        locationSubtitle.setText(LOCATION_LABEL);
        locationStatus.setImageResource(R.drawable.ic_check);
        locationStatus.setAlpha(1f);
    }

    private void markLocationNotShared(boolean anyoneWasTexted) {
        locationTitle.setText("Location not shared");
        locationSubtitle.setText(anyoneWasTexted ? "Alert sent without a location" : "Couldn't get your location");
        locationStatus.setImageResource(R.drawable.ic_alert_circle);
        locationStatus.setAlpha(1f);
    }

    // ---------------------------------------------------------------
    // The alert itself
    // ---------------------------------------------------------------

    private void sendAlert() {
        new SosAlertSender(this).sendAlert((notified, locationIncluded) -> {
            if (isFinishing() || isDestroyed()) return; // the user already left; the texts still went out

            for (int i = 0; i < contacts.size(); i++) {
                if (wasNotified(contacts.get(i), notified)) {
                    markSent(statusIcons.get(i), statusLabels.get(i));
                } else {
                    markFailed(statusIcons.get(i), statusLabels.get(i));
                }
            }

            if (!notified.isEmpty() && locationIncluded) {
                markLocationShared();
            } else {
                markLocationNotShared(!notified.isEmpty());
            }
            statusSubtitle.setText(subtitleFor(notified.size(), locationIncluded));
        });
    }

    private boolean wasNotified(EmergencyContact contact, List<EmergencyContact> notified) {
        for (EmergencyContact n : notified) {
            if (n.name.equals(contact.name) && n.phoneNumber.equals(contact.phoneNumber)) return true;
        }
        return false;
    }

    private String subtitleFor(int textedCount, boolean locationIncluded) {
        if (textedCount == 0) {
            if (!SosAlertSender.hasSmsPermission(this)) {
                return "SMS permission is off, so your contacts weren't texted. Call Campus Security now.";
            }
            return "We couldn't text your contacts. Call Campus Security now.";
        }
        if (!locationIncluded) {
            return "Your contacts were alerted, but we couldn't get your location.";
        }
        return "Your location has been shared with your emergency contacts.";
    }

    // ---------------------------------------------------------------
    // Buttons
    // ---------------------------------------------------------------

    private void setUpButtons() {
        findViewById(R.id.cancelButton).setOnClickListener(v -> {
            // Closes this page and returns to the Dashboard. Texts already sent can't be taken back.
            handler.removeCallbacks(timerTick);
            finish();
        });

        findViewById(R.id.callSecurityRow).setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + CAMPUS_SECURITY_NUMBER))));

        // TODO (teammates): the bell (R.id.notificationsButton) opens their Notifications screen
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(timerTick);
    }
}
