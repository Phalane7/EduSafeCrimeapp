package com.example.edusafe.ui.sos;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.edusafe.R;
import com.example.edusafe.data.LocationHelper;
import com.example.edusafe.data.SosAlertSender;

import java.util.List;
import java.util.Locale;

/** Legacy standalone SOS screen. MainActivity uses EmergencySosFragment. */
public class EmergencySosActivity extends AppCompatActivity {
    private static final long HOLD_DURATION_MS = 4000L;
    private static final int REQUEST_SOS_PERMISSIONS = 201;
    private static final int REQUEST_VOICE_INPUT = 202;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_sos);

        HoldProgressButton holdButton = findViewById(R.id.holdProgressRing);
        holdButton.setHoldDurationMs(HOLD_DURATION_MS);
        holdButton.setOnHoldCompleteListener(this::triggerSos);

        findViewById(R.id.voicePill).setOnClickListener(v -> startVoiceRecognition());
        // In the main app, EmergencySosFragment handles the news feed button.
        findViewById(R.id.notificationsButton).setOnClickListener(v ->
                Toast.makeText(this, "Open the Safety News Feed from the main screen", Toast.LENGTH_SHORT).show());

        requestSosPermissionsIfNeeded();
    }

    private void triggerSos() {
        // SosActiveActivity sends real alerts. Do not test with real contacts unintentionally.
        startActivity(new Intent(this, SosActiveActivity.class));
    }

    private void requestSosPermissionsIfNeeded() {
        if (SosAlertSender.hasSmsPermission(this) && LocationHelper.hasLocationPermission(this)) return;
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.SEND_SMS,
                Manifest.permission.ACCESS_FINE_LOCATION
        }, REQUEST_SOS_PERMISSIONS);
    }

    private void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say Help me to trigger SOS");
        try {
            startActivityForResult(intent, REQUEST_VOICE_INPUT);
        } catch (Exception e) {
            Toast.makeText(this, "No speech recognizer available on this device",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    @Deprecated
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_VOICE_INPUT || resultCode != Activity.RESULT_OK || data == null) return;
        List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) return;
        String heard = results.get(0).toLowerCase(Locale.getDefault());
        if (heard.contains("help")) {
            triggerSos();
        } else {
            Toast.makeText(this, "Heard: " + results.get(0) + " — say help me to trigger SOS",
                    Toast.LENGTH_LONG).show();
        }
    }
}

