package com.example.edusafe.data;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


public class LocationHelper {

    public interface LocationCallback {
        void onLocation(Location location); // null if unavailable
    }

    private final Context context;
    private final LocationManager locationManager;

    public LocationHelper(Context context) {
        this.context = context.getApplicationContext();
        this.locationManager = (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);
    }

    public static boolean hasLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Returns the best available location: prefers the last known fix
     * (instant, no wait) and falls back to a one-shot live update if
     * nothing is cached yet.
     */
    public void getCurrentLocation(LocationCallback callback) {
        if (!hasLocationPermission(context)) {
            callback.onLocation(null);
            return;
        }

        Location best = null;
        for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            if (!locationManager.isProviderEnabled(provider)) continue;
            try {
                Location last = locationManager.getLastKnownLocation(provider);
                if (last != null && (best == null || last.getAccuracy() < best.getAccuracy())) {
                    best = last;
                }
            } catch (SecurityException ignored) {
                // permission was revoked between the check and the call
            }
        }

        if (best != null) {
            callback.onLocation(best);
            return;
        }

        // No cached fix — request a single live update instead of giving up.
        requestSingleUpdate(callback);
    }

    private void requestSingleUpdate(LocationCallback callback) {
        String provider = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;

        if (!locationManager.isProviderEnabled(provider)) {
            callback.onLocation(null);
            return;
        }

        try {
            locationManager.requestSingleUpdate(provider, new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    callback.onLocation(location);
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) { }

                @Override
                public void onProviderEnabled(String provider) { }

                @Override
                public void onProviderDisabled(String provider) {
                    callback.onLocation(null);
                }
            }, Looper.getMainLooper());
        } catch (SecurityException e) {
            callback.onLocation(null);
        }
    }

    /** A plain Google Maps link, suitable for pasting straight into an SMS. */
    public static String toMapsLink(Location location) {
        return "https://maps.google.com/?q=" + location.getLatitude() + "," + location.getLongitude();
    }
}
