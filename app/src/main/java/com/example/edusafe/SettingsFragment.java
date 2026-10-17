package com.example.edusafe;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

public class SettingsFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_settings, container, false);
        SharedPreferences prefs = requireContext().getSharedPreferences("app_settings", 0);
        bind(v.findViewById(R.id.switchLocation), prefs, "include_report_location", true);
        bind(v.findViewById(R.id.switchConfirmSos), prefs, "confirm_sos", true);
        return v;
    }

    private void bind(SwitchCompat toggle, SharedPreferences prefs, String key, boolean defaultValue) {
        toggle.setChecked(prefs.getBoolean(key, defaultValue));
        toggle.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean(key, checked).apply());
    }
}
