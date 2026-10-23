package com.example.edusafe.ui.sos;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.example.edusafe.R;
import com.example.edusafe.data.LocationHelper;
import com.example.edusafe.data.SosActiveLookup;
import com.example.edusafe.data.SosAlertSender;

import java.util.List;
import java.util.Locale;

public class EmergencySosFragment extends Fragment {
    private static final int REQUEST_VOICE_INPUT = 202;
    private static final int REQUEST_SOS_PERMISSIONS = 201;

    private TextView liveSosButton;
    private SosActiveLookup.Alert activeAlert;
    private boolean checkingActiveSos;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_emergency_sos, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        HoldProgressButton hold = view.findViewById(R.id.holdProgressRing);
        hold.setHoldDurationMs(4000L);
        hold.setOnHoldCompleteListener(this::triggerSos);
        view.findViewById(R.id.voicePill).setOnClickListener(v -> triggerSos(true));

        liveSosButton = view.findViewById(R.id.liveSosButton);
        liveSosButton.setVisibility(View.GONE);
        liveSosButton.setOnClickListener(v -> {
            if (activeAlert == null) return;
            Intent intent = new Intent(requireContext(), SosActiveActivity.class);
            intent.putExtra(SosActiveActivity.EXTRA_REOPEN_SOS, true);
            intent.putExtra(SosActiveActivity.EXTRA_ALERT_ID, activeAlert.id);
            intent.putExtra(SosActiveActivity.EXTRA_CREATED_AT, activeAlert.createdAt);
            startActivity(intent);
        });

        if (!SosAlertSender.hasSmsPermission(requireContext()) ||
                !LocationHelper.hasLocationPermission(requireContext())) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{
                    Manifest.permission.SEND_SMS, Manifest.permission.ACCESS_FINE_LOCATION
            }, REQUEST_SOS_PERMISSIONS);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshLiveSos();
    }

    private void refreshLiveSos() {
        if (checkingActiveSos || !isAdded()) return;
        checkingActiveSos = true;
        SosActiveLookup.findActive(requireContext(), (alert, error) -> {
            checkingActiveSos = false;
            if (!isAdded() || getView() == null || liveSosButton == null) return;
            if (error != null) {
                // Do not treat a network failure as proof that the emergency ended.
                return;
            }
            activeAlert = alert;
            liveSosButton.setVisibility(alert == null ? View.GONE : View.VISIBLE);
        });
    }

    private void triggerSos() { triggerSos(false); }

    private void triggerSos(boolean recordVoice) {
        // Avoid creating a duplicate while an active alert is known.
        if (activeAlert != null) {
            Toast.makeText(requireContext(),
                    "You already have an active SOS. Use Live SOS at the top-right.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        // Check again at activation time in case an alert was created on another device.
        SosActiveLookup.findActive(requireContext(), (alert, error) -> {
            if (!isAdded()) return;
            if (error != null) {
                Toast.makeText(requireContext(), error + " Please try again.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (alert != null) {
                activeAlert = alert;
                if (liveSosButton != null) liveSosButton.setVisibility(View.VISIBLE);
                Toast.makeText(requireContext(),
                        "An SOS is already active. Use Live SOS to return to it.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            Intent sos = new Intent(requireContext(), SosActiveActivity.class);
            sos.putExtra(SosActiveActivity.EXTRA_START_VOICE_RECORDING, recordVoice);
            startActivity(sos);
        });
    }

}
