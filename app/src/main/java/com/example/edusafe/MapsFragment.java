package com.example.edusafe;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.Locale;

public class MapsFragment extends Fragment {
    private TextView tvLocation;
    private ActivityResultLauncher<String> askLocation;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        askLocation = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) updateLocation();
            else setLocation("Location permission is off. You can still search nearby places.");
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_maps, container, false);
        tvLocation = v.findViewById(R.id.tvLocation);
        v.findViewById(R.id.btnLocation).setOnClickListener(x -> requestLocation());
        v.findViewById(R.id.btnPolice).setOnClickListener(x -> openMap("police station"));
        v.findViewById(R.id.btnHospital).setOnClickListener(x -> openMap("hospital"));
        v.findViewById(R.id.btnCampusSecurity).setOnClickListener(x -> openMap("campus security"));
        v.findViewById(R.id.btnSafePlaces).setOnClickListener(x -> openMap("safe public place"));
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (tvLocation != null && ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            updateLocation();
        }
    }

    private void requestLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            updateLocation();
        } else {
            askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    @SuppressLint("MissingPermission")
    private void updateLocation() {
        if (getContext() == null || tvLocation == null) return;
        try {
            LocationManager manager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
            Location best = null;
            for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (manager.isProviderEnabled(provider)) {
                    Location candidate = manager.getLastKnownLocation(provider);
                    if (candidate != null && (best == null || candidate.getTime() > best.getTime())) best = candidate;
                }
            }
            if (best == null) {
                setLocation("No recent location. Turn on location and tap to try again.");
                return;
            }
            String coordinates = String.format(Locale.getDefault(), "%.5f, %.5f",
                    best.getLatitude(), best.getLongitude());
            setLocation("Last known location · " + coordinates);
        } catch (Exception e) {
            setLocation("Couldn't read your location. Tap to try again.");
        }
    }

    private void openMap(String query) {
        try {
            Uri uri = Uri.parse("geo:0,0?q=" + Uri.encode(query));
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            toast("No maps app is available on this device.");
        }
    }

    private void setLocation(String value) {
        if (tvLocation != null) tvLocation.setText(value);
    }

    private void toast(String message) {
        if (getContext() != null) Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
    }
}
