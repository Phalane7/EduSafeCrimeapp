package com.example.edusafe.ui.sos;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
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

import com.example.edusafe.DatabaseHelper;
import com.example.edusafe.R;
import com.example.edusafe.data.EmergencyContact;
import com.example.edusafe.data.SosAlertSender;
import com.example.edusafe.ui.common.SystemBars;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SosActiveActivity extends AppCompatActivity {

    private static final String CAMPUS_SECURITY_NUMBER = "0111112222";

    private static final int GREEN = Color.parseColor("#1F8A4C");
    private static final int RED = Color.parseColor("#B50218");
    private static final int GREY = Color.parseColor("#6B6B6B");

    private final Handler handler = new Handler(Looper.getMainLooper());

    private int elapsedSeconds = 0;

    private TextView timerText;
    private TextView statusTitle;
    private TextView statusSubtitle;
    private TextView locationTitle;
    private TextView locationSubtitle;

    private ImageView locationStatus;

    private final List<DatabaseHelper.Contact> contacts =
            new ArrayList<>();

    private final List<ImageView> statusIcons =
            new ArrayList<>();

    private final List<TextView> statusLabels =
            new ArrayList<>();

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

        SystemBars.applyPadded(
                this,
                findViewById(R.id.rootLayout),
                false
        );

        timerText = findViewById(R.id.timerText);
        statusTitle = findViewById(R.id.statusTitle);
        statusSubtitle = findViewById(R.id.statusSubtitle);
        locationTitle = findViewById(R.id.locationTitle);
        locationSubtitle = findViewById(R.id.locationSubtitle);
        locationStatus = findViewById(R.id.locationStatus);

        statusTitle.setText("SOS activated");
        statusSubtitle.setText("Preparing emergency alerts...");

        updateTimerText();
        handler.postDelayed(timerTick, 1000);

        pulseTheLiveDot();

        loadSavedContacts();
        showContacts();

        markLocationSending();
        setUpButtons();

        sendAlert();
    }

    // ====================================================
    // LOAD SAVED CONTACTS
    // ====================================================

    private void loadSavedContacts() {

        contacts.clear();

        try {
            DatabaseHelper db = DatabaseHelper.get(this);
            contacts.addAll(db.getContacts());

        } catch (Exception e) {
            statusSubtitle.setText(
                    "Couldn't load saved emergency contacts."
            );
        }
    }

    // ====================================================
    // DISPLAY CONTACTS
    // ====================================================

    private void showContacts() {

        LinearLayout container =
                findViewById(R.id.contactsList);

        container.removeAllViews();

        statusIcons.clear();
        statusLabels.clear();

        // Campus Security always appears first.
        for (DatabaseHelper.Contact contact : contacts) {
            if (contact.locked) {
                addContactCard(container, contact);
            }
        }

        // Keep the exact heading Parent/Guardian.
        addSection(container, "Parent/Guardian", "Guardian");

        addSection(container, "Trusted Contacts", "Friend");

        addSection(container, "Other Contacts", "Other");
    }

    private void addSection(
            LinearLayout container,
            String heading,
            String relationship) {

        List<DatabaseHelper.Contact> matching =
                new ArrayList<>();

        for (DatabaseHelper.Contact contact : contacts) {

            if (!contact.locked
                    && relationship.equalsIgnoreCase(
                    contact.relationship == null
                            ? ""
                            : contact.relationship.trim())) {

                matching.add(contact);
            }
        }

        TextView title = new TextView(this);

        title.setText(heading);
        title.setTextSize(16);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        titleParams.topMargin = dp(16);
        titleParams.bottomMargin = dp(8);

        container.addView(title, titleParams);

        if (matching.isEmpty()) {

            TextView empty = new TextView(this);

            empty.setText("No contacts added yet");
            empty.setTextColor(Color.WHITE);
            empty.setAlpha(0.8f);
            empty.setTextSize(13);
            empty.setPadding(dp(8), dp(8), dp(8), dp(8));

            container.addView(empty);
            return;
        }

        for (DatabaseHelper.Contact contact : matching) {
            addContactCard(container, contact);
        }
    }

    private void addContactCard(
            LinearLayout container,
            DatabaseHelper.Contact contact) {

        View row = LayoutInflater.from(this).inflate(
                R.layout.item_alert_contact,
                container,
                false
        );

        TextView avatar = row.findViewById(R.id.contactAvatar);
        TextView name = row.findViewById(R.id.contactName);
        TextView details = row.findViewById(R.id.contactDetails);

        ImageView icon = row.findViewById(R.id.contactStatus);
        TextView status = row.findViewById(R.id.contactStatusText);

        avatar.setText(initialsFor(contact.name));
        name.setText(contact.name);

        String number = contact.phone == null
                ? ""
                : contact.phone.trim();

        details.setText(
                "Priority " + contact.priority
                        + " · "
                        + formatPhone(number)
        );

        markSending(icon, status);

        statusIcons.add(icon);
        statusLabels.add(status);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.bottomMargin = dp(8);

        container.addView(row, params);
    }

    // ====================================================
    // CONTACT HELPERS
    // ====================================================

    private String initialsFor(String name) {

        if (name == null || name.trim().isEmpty()) {
            return "?";
        }

        StringBuilder initials = new StringBuilder();

        for (String word : name.trim().split("\\s+")) {

            if (!word.isEmpty() && initials.length() < 2) {
                initials.append(
                        Character.toUpperCase(word.charAt(0))
                );
            }
        }

        return initials.toString();
    }

    private String formatPhone(String number) {

        if (number == null || number.isEmpty()) {
            return "Number unavailable";
        }

        if (number.length() == 10) {
            return number.substring(0, 3)
                    + " "
                    + number.substring(3, 6)
                    + " "
                    + number.substring(6);
        }

        return number;
    }

    private int dp(int value) {

        return (int) (
                value * getResources()
                        .getDisplayMetrics().density
        );
    }

    // ====================================================
    // SMS STATUS
    // ====================================================

    private void markSending(
            ImageView icon,
            TextView label) {

        icon.setImageResource(R.drawable.ic_check);
        icon.setAlpha(0.25f);

        label.setText("Checking alert...");
        label.setTextColor(GREY);
    }

    // UPDATED: SMS requested, not confirmed delivered.
    private void markSent(
            ImageView icon,
            TextView label) {

        icon.setImageResource(R.drawable.ic_check);
        icon.setAlpha(1f);

        label.setText("SMS requested");
        label.setTextColor(GREEN);
    }

    private void markFailed(
            ImageView icon,
            TextView label) {

        icon.setImageResource(R.drawable.ic_alert_circle);
        icon.setAlpha(1f);

        label.setText("Not confirmed");
        label.setTextColor(RED);
    }

    // ====================================================
    // LOCATION STATUS
    // ====================================================

    private void markLocationSending() {

        locationTitle.setText("Checking your location...");
        locationSubtitle.setText("Waiting for location");

        locationStatus.setImageResource(R.drawable.ic_check);
        locationStatus.setAlpha(0.25f);
    }

    private void markLocationShared() {

        locationTitle.setText("Location included in SMS request");

        locationSubtitle.setText(
                "Location was included in the requested SMS."
        );

        locationStatus.setImageResource(R.drawable.ic_check);
        locationStatus.setAlpha(1f);
    }

    private void markLocationNotShared(boolean anyoneWasTexted) {

        locationTitle.setText("Location not shared");

        locationSubtitle.setText(
                anyoneWasTexted
                        ? "SMS requested without location"
                        : "No location alert confirmed"
        );

        locationStatus.setImageResource(
                R.drawable.ic_alert_circle
        );

        locationStatus.setAlpha(1f);
    }

    // ====================================================
    // SEND SOS ALERT
    // ====================================================

    private void sendAlert() {

        new SosAlertSender(this).sendAlert(
                (notified, locationIncluded) -> {

                    runOnUiThread(() -> {

                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        int confirmedCount = 0;

                        for (int i = 0; i < contacts.size(); i++) {

                            DatabaseHelper.Contact contact =
                                    contacts.get(i);

                            boolean submitted =
                                    wasNotified(contact, notified);

                            if (submitted) {
                                confirmedCount++;

                                markSent(
                                        statusIcons.get(i),
                                        statusLabels.get(i)
                                );

                            } else {

                                markFailed(
                                        statusIcons.get(i),
                                        statusLabels.get(i)
                                );
                            }
                        }

                        if (confirmedCount > 0 && locationIncluded) {
                            markLocationShared();
                        } else {
                            markLocationNotShared(confirmedCount > 0);
                        }

                        if (confirmedCount == 0) {

                            statusTitle.setText(
                                    "SOS alert not confirmed"
                            );

                            statusSubtitle.setText(
                                    "No SMS send request was confirmed. "
                                            + "Use the call button if you need help."
                            );

                        } else {

                            statusTitle.setText("SOS activated");

                            // UPDATED STATUS MESSAGE
                            statusSubtitle.setText(
                                    confirmedCount
                                            + " SMS send request(s) submitted."
                            );
                        }
                    });
                }
        );
    }

    private boolean wasNotified(
            DatabaseHelper.Contact contact,
            List<EmergencyContact> notified) {

        if (notified == null) return false;

        for (EmergencyContact sent : notified) {

            if (sent.phoneNumber != null
                    && contact.phone != null
                    && normalizePhone(sent.phoneNumber).equals(
                    normalizePhone(contact.phone))) {

                return true;
            }
        }

        return false;
    }

    private String normalizePhone(String number) {

        String cleaned = number.replaceAll("[\\s-]", "");

        if (cleaned.startsWith("+27")) {
            return "0" + cleaned.substring(3);
        }

        return cleaned;
    }

    // ====================================================
    // TIMER
    // ====================================================

    private void updateTimerText() {

        int minutes = elapsedSeconds / 60;
        int seconds = elapsedSeconds % 60;

        timerText.setText(
                String.format(
                        Locale.getDefault(),
                        "%02d:%02d",
                        minutes,
                        seconds
                )
        );
    }

    // ====================================================
    // LIVE DOT ANIMATION
    // ====================================================

    private void pulseTheLiveDot() {

        View dot = findViewById(R.id.liveDot);

        AlphaAnimation pulse =
                new AlphaAnimation(1f, 0.2f);

        pulse.setDuration(700);
        pulse.setRepeatMode(Animation.REVERSE);
        pulse.setRepeatCount(Animation.INFINITE);

        dot.startAnimation(pulse);
    }

    // ====================================================
    // BUTTONS
    // ====================================================

    private void setUpButtons() {

        findViewById(R.id.cancelButton)
                .setOnClickListener(v -> {

                    handler.removeCallbacks(timerTick);

                    // Closing the screen cannot recall SMS requests.
                    finish();
                });

        findViewById(R.id.callSecurityRow)
                .setOnClickListener(v -> {

                    Intent intent = new Intent(
                            Intent.ACTION_DIAL,
                            Uri.parse(
                                    "tel:" + CAMPUS_SECURITY_NUMBER
                            )
                    );

                    startActivity(intent);
                });
    }

    @Override
    protected void onDestroy() {

        handler.removeCallbacks(timerTick);

        super.onDestroy();
    }
}


