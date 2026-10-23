package com.example.edusafe;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ReportIncidentFragment extends Fragment {
    private static final String TAG = "EduSafe";
    private static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;
    private static final long MAX_VIDEO_BYTES = 25L * 1024 * 1024;
    private static final String[] TYPES = {"Robbery", "Theft", "Assault", "Harassment", "Other"};

    private String selectedType;
    private final List<TextView> chips = new ArrayList<>();
    private EditText etDesc;
    private ImageView ivPhoto, tvDot;
    private TextView tvPhoto, tvLocation, tvCode;
    private View formGroup, successGroup;
    private String mediaPath;
    private Double lat, lng;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private boolean savedCurrentMedia = false;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickMedia =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), this::onMediaPicked);

    private final ActivityResultLauncher<String> askLocation =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!isAdded() || getView() == null) return;
                if (granted) startLocation();
                else showLocation(false, "Location permission denied. Tap to retry");
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_report, container, false);
        etDesc = v.findViewById(R.id.etDesc);
        ivPhoto = v.findViewById(R.id.ivPhoto);
        tvPhoto = v.findViewById(R.id.tvPhoto);
        tvDot = v.findViewById(R.id.tvDot);
        tvLocation = v.findViewById(R.id.tvLocation);
        tvCode = v.findViewById(R.id.tvCode);
        formGroup = v.findViewById(R.id.formGroup);
        successGroup = v.findViewById(R.id.successGroup);
        tvPhoto.setText("Add photo or video");
        v.findViewById(R.id.tilePhoto).setContentDescription("Add a photo or video");

        chips.clear();
        selectedType = null;
        LinearLayout chipRow = v.findViewById(R.id.chipRow);
        chipRow.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < TYPES.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                chipRow.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            TextView chip = makeChip(TYPES[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            if (i == TYPES.length - 1) {
                lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            }
            chip.setLayoutParams(lp);
            chip.setGravity(Gravity.CENTER);
            row.addView(chip);
            chips.add(chip);
        }

        v.findViewById(R.id.tilePhoto).setOnClickListener(x ->
                pickMedia.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE)
                        .build()));
        v.findViewById(R.id.tileLocation).setOnClickListener(x -> tagLocation());
        v.findViewById(R.id.btnSubmit).setOnClickListener(x -> submit());
        v.findViewById(R.id.btnAnother).setOnClickListener(x -> resetForm());
        tagLocation();
        return v;
    }

    @Override
    public void onDestroyView() {
        stopLocation();
        if (!savedCurrentMedia) discardUnsavedMedia();
        super.onDestroyView();
    }

    private TextView makeChip(String label) {
        TextView tv = new TextView(requireContext());
        tv.setText(label);
        tv.setTextSize(14);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setMinHeight(dp(44));
        tv.setContentDescription(label + " incident type");
        styleChip(tv, false);
        tv.setOnClickListener(x -> {
            selectedType = label;
            for (TextView c : chips) styleChip(c, c == tv);
        });
        return tv;
    }

    private void styleChip(TextView tv, boolean on) {
        tv.setBackgroundResource(on ? R.drawable.report_category_selected : R.drawable.report_category_normal);
        tv.setTextColor(requireContext().getColor(on ? R.color.white : R.color.navy));
        tv.setPadding(dp(8), dp(8), dp(8), dp(8));
    }

    private boolean locationEnabledInSettings() {
        return requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .getBoolean("include_report_location", true);
    }

    private void tagLocation() {
        stopLocation();
        lat = null;
        lng = null;
        if (!locationEnabledInSettings()) {
            showLocation(false, "Location excluded in Settings");
            return;
        }
        showLocation(false, "Locating…");
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }
        startLocation();
    }

    private void startLocation() {
        if (!isAdded() || getView() == null || !locationEnabledInSettings()) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            showLocation(false, "Location permission needed. Tap to retry");
            return;
        }
        try {
            stopLocation();
            locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) {
                showLocation(false, "Location unavailable");
                return;
            }
            Location best = null;
            String provider = null;
            for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (locationManager.isProviderEnabled(p)) {
                    if (provider == null) provider = p;
                    Location l = locationManager.getLastKnownLocation(p);
                    if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                }
            }
            if (best != null) {
                onFix(best);
                return;
            }
            if (provider == null) {
                showLocation(false, "Location is switched off. Tap to retry");
                return;
            }
            locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(@NonNull Location location) {
                    onFix(location);
                    stopLocation();
                }
            };
            locationManager.requestLocationUpdates(provider, 0, 0, locationListener);
        } catch (SecurityException e) {
            Log.w(TAG, "Location permission unavailable", e);
            showLocation(false, "Location permission needed. Tap to retry");
        } catch (Exception e) {
            Log.e(TAG, "Location failed", e);
            showLocation(false, "Couldn't get location. Tap to retry");
        }
    }

    private void onFix(Location location) {
        if (!isAdded() || getView() == null || !locationEnabledInSettings()) return;
        lat = location.getLatitude();
        lng = location.getLongitude();
        showLocation(true, "Location tagged");
    }

    private void stopLocation() {
        try {
            if (locationManager != null && locationListener != null)
                locationManager.removeUpdates(locationListener);
        } catch (Exception e) {
            Log.w(TAG, "stopLocation", e);
        }
        locationListener = null;
    }

    private void showLocation(boolean ok, String message) {
        if (getView() == null) return;
        tvLocation.setText(message);
        tvDot.setImageTintList(ColorStateList.valueOf(
                requireContext().getColor(ok ? R.color.navy : R.color.muted)));
    }

    private void onMediaPicked(Uri uri) {
        if (uri == null || getView() == null) return;
        File out = null;
        try {
            String mime = requireContext().getContentResolver().getType(uri);
            boolean video = mime != null && mime.startsWith("video/");
            if (mime == null || (!video && !mime.startsWith("image/"))) {
                toast("Please select a photo or video.");
                return;
            }
            long limit = video ? MAX_VIDEO_BYTES : MAX_PHOTO_BYTES;
            String limitText = video ? "25 MB" : "5 MB";
            if (querySize(uri) > limit) {
                toast("That file is too large. The limit is " + limitText + ".");
                return;
            }
            File dir = new File(requireContext().getFilesDir(), "reports");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create reports folder");
            out = new File(dir, "media_" + UUID.randomUUID() + (video ? ".mp4" : ".jpg"));
            try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
                 OutputStream os = new FileOutputStream(out)) {
                if (in == null) throw new IllegalStateException("Cannot open file");
                byte[] buffer = new byte[8192];
                int count;
                long total = 0;
                while ((count = in.read(buffer)) != -1) {
                    total += count;
                    if (total > limit) {
                        throw new IllegalArgumentException("File exceeds " + limitText);
                    }
                    os.write(buffer, 0, count);
                }
            }
            Bitmap preview;
            if (video) {
                preview = videoFrame(out.getAbsolutePath());
            } else {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 4;
                preview = BitmapFactory.decodeFile(out.getAbsolutePath(), options);
                if (preview == null) throw new IllegalArgumentException("Not a valid image");
            }
            discardUnsavedMedia();
            mediaPath = out.getAbsolutePath();
            savedCurrentMedia = false;
            if (preview != null) {
                ivPhoto.setImageTintList(null);
                ivPhoto.setImageBitmap(preview);
            } else {
                ivPhoto.setImageTintList(null);
                ivPhoto.setImageResource(android.R.drawable.ic_media_play);
            }
            tvPhoto.setText(video ? "Video added. Tap to change" : "Photo added. Tap to change");
        } catch (Exception e) {
            if (out != null && out.exists()) out.delete();
            Log.e(TAG, "Media failed", e);
            toast("Couldn't add that file. Check its type and size.");
        }
    }

    private Bitmap videoFrame(String path) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(path);
            Bitmap frame = retriever.getFrameAtTime(0);
            if (frame == null) return null;
            int width = 320;
            int height = Math.max(1, (int) (frame.getHeight() * (width / (float) frame.getWidth())));
            Bitmap scaled = Bitmap.createScaledBitmap(frame, width, height, true);
            if (scaled != frame) frame.recycle();
            return scaled;
        } catch (Exception e) {
            Log.w(TAG, "Video preview failed", e);
            return null;
        } finally {
            try { retriever.release(); } catch (Exception ignored) { }
        }
    }

    private long querySize(Uri uri) {
        try (Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (column >= 0 && !cursor.isNull(column)) return cursor.getLong(column);
            }
        } catch (Exception e) {
            Log.w(TAG, "querySize", e);
        }
        return -1;
    }

    private void submit() {
        String description = etDesc.getText().toString().trim();
        if (selectedType == null) {
            toast("Please choose the type of incident.");
            return;
        }
        if (description.length() < 10) {
            etDesc.setError("Please describe what happened (at least 10 characters).");
            etDesc.requestFocus();
            return;
        }
        try {
            if (!locationEnabledInSettings()) {
                stopLocation();
                lat = null;
                lng = null;
            }
            DatabaseHelper.Report report = DatabaseHelper.get(requireContext()).createReport(
                    selectedType, description, lat != null ? "GPS location" : "Not tagged",
                    lat, lng, mediaPath);
            if (report == null) {
                toast("Couldn't save your report. Please try again.");
                return;
            }
            savedCurrentMedia = true;
            tvCode.setText(report.trackingCode);
            formGroup.setVisibility(View.GONE);
            successGroup.setVisibility(View.VISIBLE);
            stopLocation();
            try {
                SyncManager.syncAll(requireContext(), null);
            } catch (Exception e) {
                Log.w(TAG, "Sync could not start; report is saved locally", e);
            }
        } catch (Exception e) {
            Log.e(TAG, "Submit failed", e);
            toast("Something went wrong. Your report was not saved.");
        }
    }

    private void discardUnsavedMedia() {
        if (!savedCurrentMedia && mediaPath != null) {
            File file = new File(mediaPath);
            File reportsDir = new File(requireContext().getFilesDir(), "reports");
            if (reportsDir.equals(file.getParentFile())) file.delete();
        }
    }

    private void resetForm() {
        discardUnsavedMedia();
        mediaPath = null;
        savedCurrentMedia = false;
        etDesc.setText("");
        etDesc.setError(null);
        selectedType = null;
        for (TextView chip : chips) styleChip(chip, false);
        ivPhoto.setImageResource(R.drawable.ic_camera);
        ivPhoto.setImageTintList(ColorStateList.valueOf(requireContext().getColor(R.color.navy)));
        tvPhoto.setText("Add photo or video");
        successGroup.setVisibility(View.GONE);
        formGroup.setVisibility(View.VISIBLE);
        tagLocation();
    }

    private void toast(String message) {
        if (getContext() != null) Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}

