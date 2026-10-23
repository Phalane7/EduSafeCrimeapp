package com.example.edusafe.ui.sos;

import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.widget.Button;
import android.view.Gravity;
import android.widget.FrameLayout;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;
import java.io.File;
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
import androidx.annotation.NonNull;

import com.example.edusafe.DatabaseHelper;
import com.example.edusafe.R;
import com.example.edusafe.data.EmergencyContact;
import com.example.edusafe.data.SosAlertSender;
import com.example.edusafe.data.SosActiveLookup;
import com.example.edusafe.data.SosSupabaseSender;
import android.widget.Toast;
import com.example.edusafe.ui.common.SystemBars;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class SosActiveActivity extends AppCompatActivity {

    public static final String EXTRA_START_VOICE_RECORDING = "start_voice_recording";
    private static final int REQUEST_RECORD_AUDIO = 710;
    private MediaRecorder voiceRecorder;
    private File voiceFile;
    private Button voiceButton;
    private boolean voiceUploading;
    private boolean wantsVoice;

    private static final String CAMPUS_SECURITY_NUMBER = "0713247336";

    private static final int GREEN = Color.parseColor("#1F8A4C");
    private static final int RED = Color.parseColor("#B50218");
    private static final int GREY = Color.parseColor("#6B6B6B");

    private final Handler handler = new Handler(Looper.getMainLooper());

    public static final String EXTRA_REOPEN_SOS = "reopen_sos";
    public static final String EXTRA_ALERT_ID = "sos_alert_id";
    public static final String EXTRA_CREATED_AT = "sos_created_at";
    private boolean reopening;
    private String activeAlertId;
    private long startedAtMillis;
    private boolean resolved;
    private boolean cancellationInProgress;
    private long arrivedAtMillis = 0;
    private final Runnable statusPoll = new Runnable() {
        @Override public void run() {
            if (activeAlertId != null && !resolved && !cancellationInProgress) {
                SosActiveLookup.findActive(SosActiveActivity.this, (alert, error) -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (error != null) {
                        statusSubtitle.setText("Unable to refresh Security status. Trying again...");
                    } else if (alert == null) {
                        resolved = true;
                        updateCancelButton();
                        handler.removeCallbacks(timerTick);
                        statusTitle.setText("SOS no longer active");
                        statusSubtitle.setText("This emergency is closed or resolved.");
                    } else if (activeAlertId.equals(alert.id)) {
                        if (alert.arrivedAt != null && !alert.arrivedAt.isEmpty() && !"null".equals(alert.arrivedAt)) {
                            long arrival = parseCreatedAt(alert.arrivedAt);
                            if (arrival > 0) {
                                arrivedAtMillis = arrival;
                                handler.removeCallbacks(timerTick);
                                updateTimerText();
                            }
                        }
                        displaySecurityStatus(alert.status);
                    } else {
                        statusSubtitle.setText("Your active SOS has changed. Return to the SOS screen.");
                    }
                });
            }
            if (!resolved) handler.postDelayed(this, 8000L);
        }
    };

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
            updateTimerText();
            if (arrivedAtMillis == 0 && !resolved) handler.postDelayed(this, 1000);
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

        reopening = getIntent().getBooleanExtra(EXTRA_REOPEN_SOS, false);
        activeAlertId = getIntent().getStringExtra(EXTRA_ALERT_ID);
        startedAtMillis = parseCreatedAt(getIntent().getStringExtra(EXTRA_CREATED_AT));
        if (savedInstanceState != null) {
            startedAtMillis = savedInstanceState.getLong("started_at", startedAtMillis);
            activeAlertId = savedInstanceState.getString("active_alert_id", activeAlertId);
            reopening = savedInstanceState.getBoolean("reopening", reopening);
            arrivedAtMillis = savedInstanceState.getLong("arrived_at", 0);
        }
        if (startedAtMillis <= 0) startedAtMillis = System.currentTimeMillis();
        statusTitle.setText("SOS activated");
        statusSubtitle.setText(reopening ? "Checking Campus Security response..." : "Preparing emergency alerts...");

        updateTimerText();
        handler.postDelayed(timerTick, 1000);

        pulseTheLiveDot();

        loadSavedContacts();
        showContacts();

        markLocationSending();
        setUpButtons();
        wantsVoice = getIntent().getBooleanExtra(EXTRA_START_VOICE_RECORDING, false);
        if (wantsVoice) setupVoiceButton();
        updateCancelButton();

        if (reopening) {
            // Never send SMS or create a second Supabase row when using Live SOS.
            locationTitle.setText("Original emergency location");
            locationSubtitle.setText("See the original alert on Campus Security's dashboard.");
            for (int i = 0; i < statusLabels.size(); i++) {
                statusLabels.get(i).setText("Contact saved · previous SMS status unavailable");
            }
            if (activeAlertId != null) handler.post(statusPoll);
        } else if (savedInstanceState == null) {
            sendAlert(); // Only on first creation, never on reopening or rotation.
            SosSupabaseSender.sendWithId(this, (success, message, alertId) -> {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                if (success && alertId != null) {
                    activeAlertId = alertId;
                    if (voiceFile != null && voiceRecorder == null) uploadVoice();
                    updateCancelButton();
                    handler.removeCallbacks(statusPoll);
                    handler.post(statusPoll);
                }
            });
        } else if (activeAlertId != null) {
            handler.post(statusPoll);
        }
    }

    private void displaySecurityStatus(String status) {
        switch (status) {
            case "Active":
                statusTitle.setText("SOS activated");
                statusSubtitle.setText("Waiting for Campus Security response.");
                break;
            case "Under Review":
                statusTitle.setText("Security responding");
                statusSubtitle.setText("Campus Security is reviewing your emergency.");
                break;
            case "On the Way":
                statusTitle.setText("Security on the way");
                statusSubtitle.setText("Campus Security marked their response as on the way.");
                break;
            case "Arrived":
                statusTitle.setText("Security arrived");
                statusSubtitle.setText("Campus Security has arrived. Response timer stopped.");
                break;
            default:
                statusTitle.setText("SOS activated");
                statusSubtitle.setText("Security status: " + status);
        }
    }

    private long parseCreatedAt(String value) {
        if (value == null || value.trim().isEmpty()) return 0;
        try {
            // Supabase timestamps may contain 1-6 fractional digits and timezone offsets.
            String normalized = value.trim().replaceFirst("\\.(\\d{3})\\d+", ".$1");
            if (normalized.matches(".*T\\d{2}:\\d{2}:\\d{2}[+-]\\d{2}$")) normalized += ":00";
            if (normalized.endsWith("Z") && !normalized.contains(".")) normalized = normalized.replace("Z", ".000Z");
            if (!normalized.contains(".") && normalized.matches(".*[+-]\\d{2}:\\d{2}$")) {
                normalized = normalized.replaceFirst("([+-]\\d{2}:\\d{2})$", ".000$1");
            }
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date parsed = format.parse(normalized);
            return parsed == null ? 0 : parsed.getTime();
        } catch (Exception ignored) { return 0; }
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putLong("started_at", startedAtMillis);
        outState.putString("active_alert_id", activeAlertId);
        outState.putBoolean("reopening", reopening);
        outState.putLong("arrived_at", arrivedAtMillis);
        super.onSaveInstanceState(outState);
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

        long elapsedSeconds = Math.max(0, ((arrivedAtMillis > 0 ? arrivedAtMillis : System.currentTimeMillis()) - startedAtMillis) / 1000);
        long minutes = elapsedSeconds / 60;
        long seconds = elapsedSeconds % 60;

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

    private void updateCancelButton() {
        TextView cancel = findViewById(R.id.cancelButton);
        boolean enabled = activeAlertId != null && !activeAlertId.isEmpty()
                && !resolved && !cancellationInProgress;
        cancel.setEnabled(enabled);
        cancel.setAlpha(enabled ? 1f : 0.55f);
    }

    private void setUpButtons() {
        // Navigation is not cancellation: keep the SOS active.
        findViewById(R.id.backArrow).setOnClickListener(v -> finish());

        TextView cancelButton = findViewById(R.id.cancelButton);
        cancelButton.setText("Cancel Alert - I'm Safe");
        cancelButton.setOnClickListener(v -> {
            if (activeAlertId == null || resolved || cancellationInProgress) return;
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Cancel SOS alert?")
                    .setMessage("Only cancel if you are safe. Campus Security will see that this SOS was cancelled. SMS messages already requested cannot be recalled.")
                    .setNegativeButton("Keep SOS active", (dialog, which) -> dialog.dismiss())
                    .setPositiveButton("Yes, I'm safe", (dialog, which) -> cancelSos())
                    .show();
        });

        findViewById(R.id.callSecurityRow).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL,
                    Uri.parse("tel:" + CAMPUS_SECURITY_NUMBER));
            startActivity(intent);
        });
    }

    private void cancelSos() {
        if (activeAlertId == null || resolved || cancellationInProgress) return;
        cancellationInProgress = true;
        updateCancelButton();
        statusSubtitle.setText("Cancelling SOS with Campus Security...");

        com.example.edusafe.data.SosCancellation.cancel(this, activeAlertId,
                (success, message) -> {
                    if (isFinishing() || isDestroyed()) return;
                    cancellationInProgress = false;
                    if (success) {
                        resolved = true;
                        handler.removeCallbacks(statusPoll);
                        handler.removeCallbacks(timerTick);
                        statusTitle.setText("SOS cancelled");
                        statusSubtitle.setText("Campus Security has been updated. You are marked safe.");
                        Toast.makeText(this, "SOS cancelled successfully", Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        updateCancelButton();
                        new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("Cancellation not confirmed")
                                .setMessage(message + "\\n\\nYour SOS may still be active. Please try again or contact Campus Security.")
                                .setPositiveButton("OK", null)
                                .show();
                    }
                });
    }


    private void setupVoiceButton() {
        android.widget.ScrollView root = findViewById(R.id.rootLayout);
        LinearLayout content = (LinearLayout) root.getChildAt(0);
        voiceButton = new Button(this);
        voiceButton.setText("Start voice recording");
        voiceButton.setTextColor(Color.BLACK);
        voiceButton.setTextSize(14);
        voiceButton.setTypeface(null, Typeface.BOLD);
        voiceButton.setAllCaps(false);
        voiceButton.setGravity(Gravity.CENTER);
        voiceButton.setPadding(dp(16), 0, dp(16), 0);

        android.graphics.drawable.GradientDrawable voiceBackground =
                new android.graphics.drawable.GradientDrawable();
        voiceBackground.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        voiceBackground.setColor(Color.WHITE);
        voiceBackground.setCornerRadius(dp(18));
        voiceBackground.setStroke(dp(1), Color.parseColor("#F0F0F0"));
        voiceButton.setBackground(voiceBackground);
        voiceButton.setElevation(dp(4));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(58));
        lp.setMargins(0, dp(12), 0, dp(24));
        content.addView(voiceButton, lp);
        root.post(() -> root.fullScroll(View.FOCUS_DOWN));
        voiceButton.setOnClickListener(v -> {
            if (voiceRecorder != null) stopVoiceRecording();
            else if (!voiceUploading && voiceFile != null) uploadVoice();
            else if (!voiceUploading) startVoiceRecording();
        });
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) startVoiceRecording();
        else ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                startVoiceRecording();
            else Toast.makeText(this, "Microphone permission is needed to record", Toast.LENGTH_LONG).show();
        }
    }

    private void startVoiceRecording() {
        if (voiceRecorder != null || voiceUploading) return;
        try {
            voiceFile = File.createTempFile("edusafe_sos_", ".m4a", getFilesDir());
            voiceRecorder = new MediaRecorder();
            voiceRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            voiceRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            voiceRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            voiceRecorder.setOutputFile(voiceFile.getAbsolutePath());
            voiceRecorder.prepare();
            voiceRecorder.start();
            voiceButton.setText("Stop & send recording");
        } catch (Exception e) {
            releaseRecorder();
            Toast.makeText(this, "Could not start recording", Toast.LENGTH_LONG).show();
        }
    }

    private void stopVoiceRecording() {
        try { voiceRecorder.stop(); }
        catch (Exception e) { if (voiceFile != null) voiceFile.delete(); voiceFile = null; }
        finally { releaseRecorder(); }
        if (voiceFile == null) {
            voiceButton.setText("Start voice recording");
            return;
        }
        if (activeAlertId == null) {
            voiceButton.setText("Waiting for SOS to reach security...");
            voiceButton.setEnabled(false);
        } else uploadVoice();
    }

    private void releaseRecorder() {
        if (voiceRecorder != null) {
            voiceRecorder.reset(); voiceRecorder.release(); voiceRecorder = null;
        }
    }

    private void uploadVoice() {
        if (voiceUploading || voiceFile == null || activeAlertId == null) return;
        voiceUploading = true;
        voiceButton.setEnabled(false);
        voiceButton.setText("Sending voice recording...");
        File file = voiceFile;
        com.example.edusafe.data.SosVoiceStorage.upload(this, activeAlertId, file, (ok, message) -> {
            voiceUploading = false;
            if (isFinishing() || isDestroyed()) return;
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            if (ok) {
                file.delete(); voiceFile = null;
                voiceButton.setText("Recording sent to security");
            } else {
                voiceButton.setText("Retry sending recording");
                voiceButton.setEnabled(true);
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (voiceRecorder != null) {
            try { voiceRecorder.stop(); } catch (Exception ignored) {}
            releaseRecorder();
        }

        handler.removeCallbacks(timerTick);
        handler.removeCallbacks(statusPoll);

        super.onDestroy();
    }
}













































