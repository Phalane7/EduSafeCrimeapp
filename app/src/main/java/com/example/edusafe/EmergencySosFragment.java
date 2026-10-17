package com.example.edusafe.ui.sos;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import com.example.edusafe.MainActivity;
import com.example.edusafe.R;
import com.example.edusafe.data.LocationHelper;
import com.example.edusafe.data.SosAlertSender;
import java.util.List;
import java.util.Locale;

public class EmergencySosFragment extends Fragment {
    private static final int REQUEST_VOICE_INPUT = 202;
    private static final int REQUEST_SOS_PERMISSIONS = 201;
    @Nullable @Override
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
        view.findViewById(R.id.voicePill).setOnClickListener(v -> startVoiceRecognition());
        view.findViewById(R.id.notificationsButton).setOnClickListener(v ->
                ((MainActivity) requireActivity()).openFeed());
        if (!SosAlertSender.hasSmsPermission(requireContext()) ||
                !LocationHelper.hasLocationPermission(requireContext())) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{
                    Manifest.permission.SEND_SMS, Manifest.permission.ACCESS_FINE_LOCATION
            }, REQUEST_SOS_PERMISSIONS);
        }
    }
    private void triggerSos() {
        startActivity(new Intent(requireContext(), SosActiveActivity.class));
    }
    private void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say Help me to trigger SOS");
        try { startActivityForResult(intent, REQUEST_VOICE_INPUT); }
        catch (Exception e) {
            Toast.makeText(requireContext(), "No speech recognizer available", Toast.LENGTH_SHORT).show();
        }
    }
    @Override @Deprecated
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_VOICE_INPUT || resultCode != Activity.RESULT_OK || data == null) return;
        List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) return;
        if (results.get(0).toLowerCase(Locale.getDefault()).contains("help")) triggerSos();
        else Toast.makeText(requireContext(), "Say 'help me' to trigger SOS", Toast.LENGTH_LONG).show();
    }
}
