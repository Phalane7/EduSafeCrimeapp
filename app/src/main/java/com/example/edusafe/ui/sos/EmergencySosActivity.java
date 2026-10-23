
package com.example.edusafe.ui.sos;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.edusafe.R;
import com.example.edusafe.data.LocationHelper;
import com.example.edusafe.data.SosAlertSender;

public class EmergencySosActivity extends AppCompatActivity {

    private static final long HOLD_DURATION_MS = 4000L;
    private static final int REQUEST_SOS_PERMISSIONS = 201;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_emergency_sos);

        HoldProgressButton holdButton =
                findViewById(R.id.holdProgressRing);

        holdButton.setHoldDurationMs(HOLD_DURATION_MS);
        holdButton.setOnHoldCompleteListener(
                () -> triggerSos(false)
        );

        findViewById(R.id.voicePill).setOnClickListener(
                v -> triggerSos(true)
        );

        requestSosPermissionsIfNeeded();
    }

    private void triggerSos(boolean recordVoice) {

        Intent intent = new Intent(
                this,
                SosActiveActivity.class
        );

        intent.putExtra(
                SosActiveActivity.EXTRA_START_VOICE_RECORDING,
                recordVoice
        );

        startActivity(intent);
    }

    private void requestSosPermissionsIfNeeded() {

        if (SosAlertSender.hasSmsPermission(this)
                && LocationHelper.hasLocationPermission(this)) {
            return;
        }

        ActivityCompat.requestPermissions(
                this,
                new String[]{
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.ACCESS_FINE_LOCATION
                },
                REQUEST_SOS_PERMISSIONS
        );
    }
}


