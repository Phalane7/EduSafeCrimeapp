package com.example.edusafe;

import android.content.res.ColorStateList;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.FrameLayout;
import android.content.Intent;
import android.net.Uri;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Instant;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.RequestBody;
import okhttp3.MediaType;
import java.net.URLEncoder;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SecurityDashboardFragment extends Fragment {
    private static final String[] FILTERS = {"All", "Received", "Under Review", "Verified", "False Alert"};
    private final OkHttpClient client = new OkHttpClient();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<RemoteReport> cachedReports = new ArrayList<>();
    private boolean loading = false;
    private boolean updating = false;
    private boolean sosLoading = false;
    private boolean sosUpdating = false;
    private LinearLayout sosListContainer;
    private View dashboardHomeContainer, sosListPage, sosDetailPage;
    private TextView sosCountText, sosDetailTitle, sosDetailStatus, sosDetailTime, sosDetailLocation, sosDetailResponse, sosListHeading, sosListSubtitle;
    private LinearLayout sosDetailActions;
    private String selectedSosId = null;
    private int sosPage = 0; // 0 dashboard, 1 list, 2 details

    private final List<RemoteSos> cachedSos = new ArrayList<>();
    private final Runnable sosRefresh = new Runnable() {
        @Override public void run() {
            if (!isAdded()) return;
            loadSos();
            main.postDelayed(this, 15000L);
        }
    };
    private final List<TextView> filterChips = new ArrayList<>();
    private String filter = "All";
    private String categoryFilter = "All";
    private LinearLayout categoryRow;
    private LinearLayout listContainer, filterRow;
    private TextView tvTotal, tvReceived, tvReview, tvResolved, tvEmpty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_security_dashboard, container, false);
        listContainer = v.findViewById(R.id.listContainer);
        sosListContainer = v.findViewById(R.id.sosListContainer);
        dashboardHomeContainer = v.findViewById(R.id.dashboardHomeContainer);
        sosListPage = v.findViewById(R.id.sosListPage);
        sosDetailPage = v.findViewById(R.id.sosDetailPage);
        sosCountText = v.findViewById(R.id.sosCountText);
        sosDetailTitle = v.findViewById(R.id.sosDetailTitle);
        sosDetailStatus = v.findViewById(R.id.sosDetailStatus);
        sosDetailTime = v.findViewById(R.id.sosDetailTime);
        sosDetailLocation = v.findViewById(R.id.sosDetailLocation);
        sosDetailResponse = v.findViewById(R.id.sosDetailResponse);
        sosListHeading = v.findViewById(R.id.sosListHeading);
        sosListSubtitle = v.findViewById(R.id.sosListSubtitle);
        sosDetailActions = v.findViewById(R.id.sosDetailActions);
        v.findViewById(R.id.sosSummaryCard).setOnClickListener(x -> showSosPage(1));
        v.findViewById(R.id.sosListBack).setOnClickListener(x -> showSosPage(sosPage == 3 ? 1 : 0));
        v.findViewById(R.id.sosDetailBack).setOnClickListener(x -> showSosPage(1));
        filterRow = v.findViewById(R.id.filterRow);
        categoryRow = v.findViewById(R.id.categoryRow);
        tvTotal = v.findViewById(R.id.tvTotal);
        tvReceived = v.findViewById(R.id.tvReceived);
        tvReview = v.findViewById(R.id.tvReview);
        tvResolved = v.findViewById(R.id.tvResolved);
        tvEmpty = v.findViewById(R.id.tvEmpty);
        buildFilters();
        loadReports();
        renderSos();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadReports();
        main.removeCallbacks(sosRefresh);
        sosRefresh.run();
    }

    @Override public void onPause() {
        main.removeCallbacks(sosRefresh);
        super.onPause();
    }

    private static class RemoteSos {
        String id, status, arrivedAt, resolvedAt;
        boolean verified;
        String voicePath;
        Double latitude, longitude;
        long createdAt;
    }

    private void loadSos() {
        if (!isAdded() || sosLoading) return;
        SharedPreferences prefs = requireContext().getSharedPreferences("edusafe_supabase_session", Context.MODE_PRIVATE);
        String token = prefs.getString("access_token", "");
        if (token == null || token.isEmpty() || System.currentTimeMillis() >= prefs.getLong("expires_at", 0)) {
            showSosMessage("Session expired. Please log in again.");
            return;
        }
        sosLoading = true;
        Request request = new Request.Builder()
                .url(SupabaseConfig.SUPABASE_URL + "/rest/v1/sos_alerts?select=id,status,latitude,longitude,created_at,arrived_at,resolved_at,verified,sos_voice_recordings(storage_path)&order=created_at.desc&limit=100")
                .header("apikey", SupabaseConfig.SUPABASE_KEY)
                .header("Authorization", "Bearer " + token)
                .get().build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                main.post(() -> { sosLoading = false; showSosMessage("Could not load SOS alerts. Check your connection."); });
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response res = response) {
                    if (!res.isSuccessful()) {
                        int code = res.code();
                        main.post(() -> { sosLoading = false; showSosMessage("SOS loading failed (HTTP " + code + ")."); });
                        return;
                    }
                    JSONArray array = new JSONArray(res.body() == null ? "[]" : res.body().string());
                    List<RemoteSos> results = new ArrayList<>();
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject o = array.getJSONObject(i);
                        RemoteSos s = new RemoteSos();
                        s.id = o.optString("id");
                        s.status = o.optString("status", "Active");
                        s.arrivedAt = o.optString("arrived_at", "");
                        s.resolvedAt = o.optString("resolved_at", "");
                        s.verified = o.optBoolean("verified", false);
                        JSONArray voices = o.optJSONArray("sos_voice_recordings");
                        if (voices != null && voices.length() > 0)
                            s.voicePath = voices.optJSONObject(0).optString("storage_path", "");
                        JSONObject singleVoice = o.optJSONObject("sos_voice_recordings");
                        if (singleVoice != null) s.voicePath = singleVoice.optString("storage_path", "");
                        if (!o.isNull("latitude") && !o.isNull("longitude")) {
                            s.latitude = o.getDouble("latitude");
                            s.longitude = o.getDouble("longitude");
                        }
                        try { s.createdAt = Instant.parse(o.optString("created_at")).toEpochMilli(); }
                        catch (Exception ignored) { s.createdAt = System.currentTimeMillis(); }
                        results.add(s);
                    }
                    main.post(() -> {
                        sosLoading = false;
                        if (!isAdded()) return;
                        cachedSos.clear();
                        cachedSos.addAll(results);
                        renderSos();
                    });
                } catch (Exception e) {
                    Log.e("EduSafe", "SOS loading failed", e);
                    main.post(() -> { sosLoading = false; showSosMessage("Could not read SOS alerts."); });
                }
            }
        });
    }

    private void showSosMessage(String message) {
        if (!isAdded() || sosListContainer == null) return;
        if (cachedSos.isEmpty()) {
            sosListContainer.removeAllViews();
            addLine(sosListContainer, message, false);
        } else Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }

    private boolean isOpenSos(String status) {
        return "Active".equals(status) || "Under Review".equals(status)
                || "On the Way".equals(status) || "Arrived".equals(status);
    }

    private void showSosPage(int page) {
        sosPage = page;
        dashboardHomeContainer.setVisibility(page == 0 ? View.VISIBLE : View.GONE);
        sosListPage.setVisibility((page == 1 || page == 3) ? View.VISIBLE : View.GONE);
        sosDetailPage.setVisibility(page == 2 ? View.VISIBLE : View.GONE);
        renderSos();
    }

    private void renderSos() {
        if (!isAdded() || sosListContainer == null) return;
        sosListContainer.removeAllViews();
        int count = 0;
        for (RemoteSos s : cachedSos) if (isOpenSos(s.status)) count++;
        sosCountText.setText(String.valueOf(count));
        if (sosPage == 3) {
            sosListHeading.setText("SOS Alert History");
            sosListSubtitle.setText("Past emergencies and their final outcomes.");
        } else {
            sosListHeading.setText("Active SOS Alerts");
            sosListSubtitle.setText("Select an emergency to view details and respond.");
        }
        if (sosPage == 1) coloredHistoryLink();
        int number = 0;
        for (int i = 0; i < cachedSos.size(); i++) {
            RemoteSos s = cachedSos.get(i);
            if ((sosPage == 3 && isOpenSos(s.status)) || (sosPage != 3 && !isOpenSos(s.status))) continue;
            number++;
            boolean active = isOpenSos(s.status);
            LinearLayout card = new LinearLayout(requireContext());
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(12), dp(15), dp(12), dp(15));
            card.setBackgroundResource(active ? R.drawable.edusafe_sos_active_card : R.drawable.edusafe_sos_white_card);
            card.setElevation(dp(3));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(12);
            sosListContainer.addView(card, lp);
            TextView avatar = new TextView(requireContext());
            avatar.setText(active ? "SOS" : "✓");
            avatar.setTextSize(active ? 12 : 21);
            avatar.setTypeface(null, Typeface.BOLD);
            avatar.setTextColor(active ? 0xFFFFFFFF : 0xFF087A61);
            avatar.setGravity(Gravity.CENTER);
            avatar.setBackgroundResource(active ? R.drawable.edusafe_sos_round_icon : R.drawable.edusafe_sos_history_icon);
            card.addView(avatar, new LinearLayout.LayoutParams(dp(45), dp(45)));
            LinearLayout content = new LinearLayout(requireContext());
            content.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1);
            clp.leftMargin = dp(11);
            card.addView(content, clp);
            LinearLayout heading = new LinearLayout(requireContext());
            heading.setGravity(Gravity.CENTER_VERTICAL);
            content.addView(heading);
            TextView title = sosText("SOS Alert #" + String.format(Locale.getDefault(), "%03d", i + 1), 14, 0xFF0B1E49, true);
            heading.addView(title);
            TextView badge = sosText(s.status + (s.verified ? " · Verified" : ""), 10, active ? 0xFFC91E51 : 0xFF087A61, true);
            badge.setPadding(dp(7), dp(4), dp(7), dp(4));
            badge.setBackgroundResource(active ? R.drawable.edusafe_sos_badge_active : R.drawable.edusafe_sos_badge_closed);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
            blp.leftMargin = dp(7);
            heading.addView(badge, blp);
            addSosSubline(content, new SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()).format(new Date(s.createdAt)));
            if (sosPage == 3 && s.resolvedAt != null && !s.resolvedAt.isEmpty() && !"null".equals(s.resolvedAt))
                addSosSubline(content, "Resolved: " + readableSosDate(s.resolvedAt));
            if (s.arrivedAt != null && !s.arrivedAt.isEmpty() && !"null".equals(s.arrivedAt))
                addSosSubline(content, "Security response: " + responseDuration(s.createdAt, s.arrivedAt));
            TextView gps = sosText(s.latitude == null ? "◇  Location unavailable" : "⌖  GPS location available", 11, s.latitude == null ? 0xFF657795 : 0xFF087B9A, true);
            gps.setPadding(dp(8), dp(5), dp(8), dp(5));
            gps.setBackgroundResource(R.drawable.edusafe_sos_gps_tile);
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-2, -2);
            glp.topMargin = dp(7);
            content.addView(gps, glp);
            if (active) {
                TextView chevron = sosText("›", 24, 0xFF516C9D, false);
                card.addView(chevron);
                card.setClickable(true);
                card.setFocusable(true);
                card.setOnClickListener(v -> { selectedSosId = s.id; showSosPage(2); });
            }
        }
        if (number == 0) {
            TextView empty = sosText(sosPage == 3 ? "No past SOS alerts yet." : "No active SOS alerts right now.", 14, 0xFF657795, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(14), dp(32), dp(14), dp(32));
            sosListContainer.addView(empty);
        }
        if (sosPage == 2) renderSosDetails();
    }

    private TextView sosText(String value, int size, int color, boolean bold) {
        TextView tv = new TextView(requireContext());
        tv.setText(value);
        tv.setTextSize(size);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(null, Typeface.BOLD);
        return tv;
    }

    private void addSosSubline(LinearLayout parent, String text) {
        TextView line = sosText(text, 11, 0xFF657795, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(4);
        parent.addView(line, lp);
    }

    private String readableSosDate(String raw) {
        try { return new SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault())
                .format(new Date(Instant.parse(raw).toEpochMilli())); }
        catch (Exception ignored) { return raw; }
    }

    private void renderSosDetails() {
        if (sosDetailActions == null) return;
        RemoteSos selected = null;
        for (RemoteSos item : cachedSos) {
            if (item.id != null && item.id.equals(selectedSosId)) { selected = item; break; }
        }
        if (selected == null || !isOpenSos(selected.status)) { showSosPage(1); return; }
        final RemoteSos s = selected;
        sosDetailTitle.setText("SOS Emergency Details");
        sosDetailStatus.setText("● " + s.status + (s.verified ? " · Verified" : ""));
        sosDetailTime.setText(new SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()).format(new Date(s.createdAt)));
        sosDetailLocation.setText(s.latitude == null ? "Unavailable" : String.format(Locale.US, "%.5f, %.5f", s.latitude, s.longitude));
        if (s.arrivedAt != null && !s.arrivedAt.isEmpty() && !"null".equals(s.arrivedAt)) {
            sosDetailResponse.setVisibility(View.VISIBLE);
            sosDetailResponse.setText("Security response: " + responseDuration(s.createdAt, s.arrivedAt));
        } else sosDetailResponse.setVisibility(View.GONE);
        sosDetailActions.removeAllViews();
        if (s.voicePath != null && !s.voicePath.isEmpty())
            coloredSosButton("▶  Play Voice Recording", "#173783", () -> playSosVoice(s.voicePath));
        if (s.latitude != null) coloredSosButton("➤  Navigate to Location", "#173783", () -> {
            String uri = "https://www.google.com/maps/dir/?api=1&destination=" + s.latitude + "," + s.longitude;
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
        });
        if ("Active".equals(s.status)) coloredSosButton("⚠  Respond to SOS", "#C55A00", () -> confirmSosStatus(s, "Under Review"));
        if ("Under Review".equals(s.status)) coloredSosButton("➤  On the Way", "#C55A00", () -> confirmSosStatus(s, "On the Way"));
        if ("On the Way".equals(s.status)) coloredSosButton("⌖  Arrived at Location", "#C55A00", () -> confirmSosStatus(s, "Arrived"));
        if ("Arrived".equals(s.status)) coloredSosButton("✓  Resolve Emergency", "#C55A00", () -> confirmSosStatus(s, "Resolved"));
        if (!s.verified) coloredSosButton("✓  Verify", "#0D8168", () -> showIncidentTypeDialog(s));
        coloredSosButton("⊘  False alert", "#FFE6ED", () -> confirmSosStatus(s, "False Alert"));
    }

    private void coloredHistoryLink() {
        LinearLayout card = new LinearLayout(requireContext());
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(14));
        card.setBackgroundResource(R.drawable.edusafe_sos_white_card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        sosListContainer.addView(card, lp);
        TextView title = sosText("◷   SOS Alert History", 14, 0xFF0B1E49, true);
        card.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(sosText("›", 24, 0xFF516C9D, false));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> showSosPage(3));
    }

    private String responseDuration(long startMillis, String arrivedAt) {
        try {
            long seconds = Math.max(0, (java.time.Instant.parse(arrivedAt).toEpochMilli() - startMillis) / 1000);
            return String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60);
        } catch (Exception ignored) { return "Unavailable"; }
    }


    private android.media.MediaPlayer voicePlayer;
    private void playSosVoice(String path) {
        if (!isAdded()) return;
        Context ctx = requireContext();
        SharedPreferences prefs = ctx.getSharedPreferences("edusafe_supabase_session", Context.MODE_PRIVATE);
        String token = prefs.getString("access_token", "");
        if (token.isEmpty()) { Toast.makeText(ctx, "Please log in again", Toast.LENGTH_LONG).show(); return; }
        Request request = new Request.Builder()
                .url(SupabaseConfig.SUPABASE_URL + "/storage/v1/object/authenticated/sos-voice/" + path)
                .header("apikey", SupabaseConfig.SUPABASE_KEY)
                .header("Authorization", "Bearer " + token).get().build();
        Toast.makeText(ctx, "Loading voice recording...", Toast.LENGTH_SHORT).show();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                main.post(() -> { if (isAdded()) Toast.makeText(requireContext(), "Could not load recording", Toast.LENGTH_LONG).show(); });
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response res = response) {
                    if (!res.isSuccessful() || res.body() == null) {
                        main.post(() -> { if (isAdded()) Toast.makeText(requireContext(), "Recording unavailable (HTTP " + res.code() + ")", Toast.LENGTH_LONG).show(); });
                        return;
                    }
                    java.io.File file = java.io.File.createTempFile("sos_play_", ".m4a", ctx.getCacheDir());
                    try (java.io.InputStream input = res.body().byteStream();
                         java.io.FileOutputStream output = new java.io.FileOutputStream(file)) {
                        byte[] buffer = new byte[8192]; int n;
                        while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
                    }
                    main.post(() -> {
                        if (!isAdded()) { file.delete(); return; }
                        try {
                            if (voicePlayer != null) { voicePlayer.release(); voicePlayer = null; }
                            voicePlayer = new android.media.MediaPlayer();
                            voicePlayer.setDataSource(file.getAbsolutePath());
                            voicePlayer.prepare();
                            voicePlayer.setOnCompletionListener(mp -> { mp.release(); voicePlayer = null; file.delete(); });
                            voicePlayer.start();
                            Toast.makeText(requireContext(), "Playing SOS recording", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) { file.delete(); Toast.makeText(requireContext(), "Unable to play recording", Toast.LENGTH_LONG).show(); }
                    });
                } catch (Exception e) { main.post(() -> { if (isAdded()) Toast.makeText(requireContext(), "Could not read recording", Toast.LENGTH_LONG).show(); }); }
            }
        });
    }

    private void coloredSosButton(String label, String color, Runnable action) {
        TextView button = new TextView(requireContext());
        button.setText(label);
        button.setTextSize(15);
        button.setTypeface(null, Typeface.BOLD);
        button.setTextColor("#FFE6ED".equals(color) ? 0xFFBD174B : android.graphics.Color.WHITE);
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(android.graphics.Color.parseColor(color));
        background.setCornerRadius(dp(15));
        if ("#FFE6ED".equals(color)) background.setStroke(dp(1), 0xFFFFA8BF);
        button.setBackground(background);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(54));
        button.setElevation(dp(3));
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        sosDetailActions.addView(button, lp);
        button.setOnClickListener(v -> action.run());
    }

    // Security selects the category before a verified SOS appears on the map.
    private void showIncidentTypeDialog(RemoteSos s) {
        if (!isAdded() || sosUpdating) return;
        if (s.latitude == null || s.longitude == null) {
            Toast.makeText(requireContext(), "This SOS has no GPS location.", Toast.LENGTH_LONG).show();
            return;
        }
        String[] categories = {"Robbery", "Theft", "Assault", "Other"};
        final int[] choice = {-1};
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Verify SOS incident")
                .setSingleChoiceItems(categories, -1, (d, which) -> choice[0] = which)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Verify & publish", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    if (choice[0] < 0) {
                        Toast.makeText(requireContext(), "Please select an incident type.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    dialog.dismiss();
                    verifyAndPublish(s, categories[choice[0]]);
                }));
        dialog.show();
    }

    // RPC checks the signed-in user's security role inside the database.
    private void verifyAndPublish(RemoteSos s, String category) {
        if (!isAdded() || sosUpdating) return;
        SharedPreferences prefs = requireContext().getSharedPreferences("edusafe_supabase_session", Context.MODE_PRIVATE);
        String token = prefs.getString("access_token", "");
        if (token == null || token.isEmpty() || System.currentTimeMillis() >= prefs.getLong("expires_at", 0)) {
            showSosMessage("Session expired. Please log in again.");
            return;
        }
        sosUpdating = true;
        try {
            JSONObject payload = new JSONObject();
            payload.put("p_sos_id", s.id);
            payload.put("p_incident_type", category);
            Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/rest/v1/rpc/verify_and_publish_sos")
                    .header("apikey", SupabaseConfig.SUPABASE_KEY)
                    .header("Authorization", "Bearer " + token)
                    .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), payload.toString()))
                    .build();
            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    main.post(() -> { sosUpdating = false; if (isAdded()) showSosMessage("Verification failed. Check your connection."); });
                }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try (Response res = response) {
                        boolean ok = res.isSuccessful() && "true".equalsIgnoreCase(res.body() == null ? "" : res.body().string().trim());
                        int code = res.code();
                        main.post(() -> {
                            sosUpdating = false;
                            if (!isAdded()) return;
                            if (ok) {
                                s.verified = true;
                                renderSos();
                                loadSos();
                                Toast.makeText(requireContext(), "Verified and published to map", Toast.LENGTH_LONG).show();
                            } else {
                                showSosMessage("Could not verify SOS (HTTP " + code + "). Check security permissions and SOS status.");
                                loadSos();
                            }
                        });
                    }
                }
            });
        } catch (Exception e) {
            sosUpdating = false;
            showSosMessage("Could not prepare verification.");
        }
    }

    private void confirmSosStatus(RemoteSos s, String status) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Mark SOS as " + status + "?")
                .setMessage(sosConfirmationMessage(status))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm", (dialog, which) -> updateSosStatus(s, status))
                .show();
    }

    private String sosConfirmationMessage(String status) {
        switch (status) {
            case "Under Review":
                return "Confirm that Campus Security has started responding to this SOS.";
            case "On the Way":
                return "Confirm that Campus Security is travelling to the student's location.";
            case "Arrived":
                return "Confirm that Campus Security has actually arrived at the location.";
            case "Resolved":
                return "Confirm that the emergency is resolved. This will remove it from the student's Live SOS list.";
            case "Verified":
                return "Confirm this emergency is genuine. Verification will not close the SOS.";
            case "False Alert":
                return "Confirm this was a false alert. This ends the current Live SOS tracking.";
            default:
                return "Confirm this SOS status change.";
        }
    }

    private void updateSosStatus(RemoteSos s, String status) {
        if (!isAdded() || sosUpdating) return;
        SharedPreferences prefs = requireContext().getSharedPreferences("edusafe_supabase_session", Context.MODE_PRIVATE);
        String token = prefs.getString("access_token", "");
        if (token == null || token.isEmpty() || System.currentTimeMillis() >= prefs.getLong("expires_at", 0)) {
            showSosMessage("Session expired. Please log in again.");
            return;
        }
        sosUpdating = true;
        try {
            JSONObject body = new JSONObject();
            if ("Verified".equals(status)) body.put("verified", true);
            else {
                body.put("status", status);
                if ("Arrived".equals(status)) body.put("arrived_at", java.time.Instant.now().toString());
                if ("Resolved".equals(status)) body.put("resolved_at", java.time.Instant.now().toString());
                if ("False Alert".equals(status)) body.put("published_to_map", false);
            }
            Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/rest/v1/sos_alerts?id=eq." + URLEncoder.encode(s.id, "UTF-8")
                            + "&status=eq." + URLEncoder.encode(s.status, "UTF-8"))
                    .header("apikey", SupabaseConfig.SUPABASE_KEY)
                    .header("Authorization", "Bearer " + token)
                    .header("Prefer", "return=representation")
                    .patch(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), body.toString()))
                    .build();
            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    main.post(() -> { sosUpdating = false; showSosMessage("SOS status update failed. Check your connection."); });
                }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try (Response res = response) {
                        int code = res.code();
                        boolean success = res.isSuccessful() && new JSONArray(res.body() == null ? "[]" : res.body().string()).length() == 1;
                        main.post(() -> {
                            sosUpdating = false;
                            if (!isAdded()) return;
                            if (success) {
                                if ("Verified".equals(status)) s.verified = true;
                                else s.status = status;
                                loadSos();
                                renderSos();
                                Toast.makeText(requireContext(), "SOS marked " + status, Toast.LENGTH_SHORT).show();
                            } else {
                                showSosMessage("SOS status not updated (HTTP " + code + ").");
                                loadSos();
                            }
                        });
                    } catch (Exception e) {
                        main.post(() -> { sosUpdating = false; showSosMessage("Could not read SOS update response."); });
                    }
                }
            });
        } catch (Exception e) {
            sosUpdating = false;
            showSosMessage("Could not prepare SOS update.");
        }
    }

    private void buildFilters() {
        filterRow.removeAllViews();
        filterChips.clear();
        for (String label : FILTERS) {
            TextView chip = new TextView(requireContext());
            chip.setText(label);
            chip.setTextSize(11);
            chip.setTypeface(null, Typeface.BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(dp(36));
            chip.setPadding(dp(12), dp(6), dp(12), dp(6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dp(8), 0);
            filterRow.addView(chip, lp);
            filterChips.add(chip);
            chip.setOnClickListener(x -> {
                filter = label;
                styleFilters();
                renderReports();
            });
        }
        styleFilters();
    }

    private void styleFilters() {
        if (getContext() == null) return;
        for (TextView chip : filterChips) {
            boolean selected = filter.equals(chip.getText().toString());
            chip.setBackgroundResource(selected ? R.drawable.edusafe_filter_active : R.drawable.edusafe_filter_inactive);
            chip.setTextColor(requireContext().getColor(selected ? R.color.white : R.color.navy));
        }
    }

    private static class RemoteReport {
        String trackingCode, type, description, location, status, mediaPath, mediaType;
        long createdAt;
    }

    private void loadReports() {
        if (!isAdded() || loading) return;
        SharedPreferences prefs = requireContext().getSharedPreferences(
                "edusafe_supabase_session", Context.MODE_PRIVATE);
        String token = prefs.getString("access_token", "");
        long expiresAt = prefs.getLong("expires_at", 0);
        if (token == null || token.isEmpty() || System.currentTimeMillis() >= expiresAt) {
            showError("Session expired. Please log in again.");
            return;
        }
        loading = true;
        if (cachedReports.isEmpty() && tvEmpty != null) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("Loading student reports...");
        }
        Request request = new Request.Builder()
                .url(SupabaseConfig.SUPABASE_URL
                        + "/rest/v1/crime_reports?select=tracking_code,type,description,location,status,created_at,media_path,media_type"
                        + "&order=created_at.desc&limit=500")
                .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + token)
                .get().build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                main.post(() -> { loading = false; showError("Couldn't load reports. Check your connection."); });
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response res = response) {
                    if (!res.isSuccessful()) {
                        int code = res.code();
                        main.post(() -> { loading = false; showError("Couldn't load reports (HTTP " + code + ")."); });
                        return;
                    }
                    String json = res.body() == null ? "[]" : res.body().string();
                    JSONArray array = new JSONArray(json);
                    List<RemoteReport> loaded = new ArrayList<>();
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject o = array.getJSONObject(i);
                        RemoteReport r = new RemoteReport();
                        r.trackingCode = o.optString("tracking_code", "");
                        r.type = o.optString("type", "Incident report");
                        r.description = o.optString("description", "");
                        r.location = o.optString("location", "");
                        r.status = o.optString("status", "Received");
                        r.mediaPath = o.optString("media_path", "");
                        r.mediaType = o.optString("media_type", "");
                        try { r.createdAt = Instant.parse(o.optString("created_at")).toEpochMilli(); }
                        catch (Exception ignored) { r.createdAt = System.currentTimeMillis(); }
                        loaded.add(r);
                    }
                    main.post(() -> {
                        loading = false;
                        if (!isAdded() || listContainer == null) return;
                        cachedReports.clear();
                        cachedReports.addAll(loaded);
                        renderReports();
                    });
                } catch (Exception e) {
                    Log.e("EduSafe", "Report loading failed", e);
                    main.post(() -> { loading = false; showError("Unable to read the reports."); });
                }
            }
        });
    }

    private void showError(String message) {
        if (!isAdded() || tvEmpty == null || listContainer == null) return;
        if (cachedReports.isEmpty()) {
            listContainer.removeAllViews();
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(message);
        } else {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            renderReports();
        }
    }

    private void renderReports() {
        if (!isAdded() || listContainer == null) return;
        int received = 0, review = 0, verified = 0, visible = 0;
        listContainer.removeAllViews();
        for (RemoteReport report : cachedReports) {
            if ("Received".equals(report.status)) received++;
            else if ("Under Review".equals(report.status)) review++;
            else if ("Verified".equals(report.status)) verified++;
            if (!"All".equals(filter) && !filter.equals(report.status)) continue;
            if (!"All".equals(categoryFilter) && !categoryFilter.equalsIgnoreCase(report.type == null ? "" : report.type.trim())) continue;
            listContainer.addView(reportCard(report));
            visible++;
        }
        tvTotal.setText(String.valueOf(cachedReports.size()));
        tvReceived.setText(String.valueOf(received));
        tvReview.setText(String.valueOf(review));
        // The existing fourth counter is reused for Verified reports.
        tvResolved.setText(String.valueOf(verified));
        renderCategories();
        tvEmpty.setVisibility(visible == 0 ? View.VISIBLE : View.GONE);
        tvEmpty.setText(cachedReports.isEmpty()
                ? "No student reports have been uploaded yet."
                : "No reports match these filters. Choose another category or status.");
    }

    private void renderCategories() {
        if (categoryRow == null || getContext() == null) return;
        categoryRow.removeAllViews();
        String[] categories = {"All", "Robbery", "Theft", "Assault", "Harassment", "Other"};
        for (String category : categories) {
            int count = 0;
            for (RemoteReport report : cachedReports) {
                if ("All".equals(category) || category.equalsIgnoreCase(report.type == null ? "" : report.type.trim())) count++;
            }
            TextView chip = new TextView(requireContext());
            chip.setText(category + "  " + count);
            chip.setTextSize(11);
            chip.setTypeface(null, Typeface.BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(dp(36));
            chip.setPadding(dp(12), dp(6), dp(12), dp(6));
            boolean selected = category.equals(categoryFilter);
            chip.setBackgroundResource(selected ? R.drawable.edusafe_filter_active : R.drawable.edusafe_filter_inactive);
            chip.setTextColor(requireContext().getColor(selected ? R.color.white : R.color.navy));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, dp(8), 0);
            categoryRow.addView(chip, params);
            chip.setOnClickListener(v -> {
                categoryFilter = category;
                renderReports();
            });
        }
    }

    private View reportCard(RemoteReport report) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.edusafe_report_card);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, dp(10), 0, 0);
        card.setLayoutParams(cardLp);

        LinearLayout heading = new LinearLayout(requireContext());
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(requireContext());
        title.setText(report.type == null ? "Incident report" : report.type);
        title.setTextColor(requireContext().getColor(R.color.ink));
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        heading.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView status = new TextView(requireContext());
        status.setText(report.status);
        status.setTextSize(11);
        status.setTypeface(null, Typeface.BOLD);
        status.setPadding(dp(10), dp(6), dp(10), dp(6));
        styleStatus(status, report.status);
        heading.addView(status);
        card.addView(heading);

        addLine(card, "Code " + report.trackingCode, true);
        addLine(card, report.location == null || report.location.trim().isEmpty()
                ? "Location not added" : report.location, false);
        addLine(card, report.description == null ? "" : report.description, false);
        String time = new SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault())
                .format(new Date(report.createdAt));
        addLine(card, time, false);
        if (report.mediaPath != null && !report.mediaPath.isEmpty())
            addLine(card, report.mediaType != null && report.mediaType.startsWith("video/")
                    ? "▶ Video attached" : "▣ Photo attached", true);

        TextView action = new TextView(requireContext());
        action.setText("View report  ›");
        action.setTextColor(requireContext().getColor(R.color.navy));
        action.setTextSize(14);
        action.setTypeface(null, Typeface.BOLD);
        action.setGravity(Gravity.CENTER_VERTICAL);
        action.setMinHeight(dp(44));
        action.setContentDescription("Review report " + report.trackingCode);
        card.addView(action);
        addStatusButtons(card, report);
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(report.type + ", " + report.status + ", tracking " + report.trackingCode);
        card.setOnClickListener(x -> showReport(report));
        return card;
    }

    private void addLine(LinearLayout card, String text, boolean emphasized) {
        if (text == null || text.trim().isEmpty()) return;
        TextView line = new TextView(requireContext());
        line.setText(text);
        line.setTextColor(requireContext().getColor(emphasized ? R.color.navy : R.color.muted));
        line.setTextSize(emphasized ? 13 : 14);
        if (emphasized) line.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(7);
        card.addView(line, lp);
    }

    private void styleStatus(TextView tv, String value) {
        int bg = "Verified".equals(value) ? R.color.ink
                : "Under Review".equals(value) ? R.color.light_blue : R.color.glass_red;
        int fg = "Under Review".equals(value) ? R.color.navy
                : "Received".equals(value) ? R.color.red : R.color.white;
        tv.setBackgroundResource(R.drawable.bg_pill);
        tv.setBackgroundTintList(ColorStateList.valueOf(requireContext().getColor(bg)));
        tv.setTextColor(requireContext().getColor(fg));
    }

    private void showReport(RemoteReport report) {
        String location = report.location == null || report.location.trim().isEmpty()
                ? "Not provided" : report.location;
        String message = "Code: " + report.trackingCode
                + "\nStatus: " + report.status
                + "\nLocation: " + location
                + "\nSubmitted: " + new SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault())
                .format(new Date(report.createdAt))
                + "\n\n" + report.description;
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(12), dp(18), dp(12));
        TextView details = new TextView(requireContext());
        details.setText(message);
        details.setTextSize(14);
        details.setTextColor(0xFF102C62);
        content.addView(details);
        if (report.mediaPath != null && !report.mediaPath.isEmpty()) {
            TextView attachment = new TextView(requireContext());
            attachment.setText(report.mediaType != null && report.mediaType.startsWith("video/")
                    ? "▶ View attached video" : "▣ View attached photo");
            attachment.setTextColor(0xFF2856C6);
            attachment.setTypeface(null, Typeface.BOLD);
            attachment.setGravity(Gravity.CENTER);
            attachment.setMinHeight(dp(52));
            content.addView(attachment);
            attachment.setOnClickListener(v -> openReportAttachment(report));
        }
        ScrollView scroll = new ScrollView(requireContext());
        scroll.addView(content);
        new AlertDialog.Builder(requireContext())
                .setTitle(report.type)
                .setView(scroll)
                .setNegativeButton("Close", null)
                .setPositiveButton("Change status", (dialog, which) -> chooseStatus(report))
                .show();
    }



    // Downloads private evidence with the security user's Supabase JWT.
    private void openReportAttachment(RemoteReport report) {
        if (!isAdded() || report.mediaPath == null || report.mediaPath.isEmpty()) return;
        SharedPreferences prefs = requireContext().getSharedPreferences(
                "edusafe_supabase_session", Context.MODE_PRIVATE);
        String jwt = prefs.getString("access_token", "");
        if (jwt == null || jwt.isEmpty() || System.currentTimeMillis() >= prefs.getLong("expires_at", 0)) {
            Toast.makeText(requireContext(), "Please log in again.", Toast.LENGTH_LONG).show();
            return;
        }
        boolean video = report.mediaType != null && report.mediaType.startsWith("video/");
        if (video) {
            // Download video into the app's private cache and open it with a temporary content URI.
            // Requires the FileProvider declaration in AndroidManifest.xml (see README).
            Toast.makeText(requireContext(), "Loading video...", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), "Loading photo...", Toast.LENGTH_SHORT).show();
        }
        Request req = new Request.Builder()
                .url(SupabaseConfig.SUPABASE_URL + "/storage/v1/object/report-media/" + report.mediaPath)
                .header("apikey", SupabaseConfig.SUPABASE_KEY)
                .header("Authorization", "Bearer " + jwt)
                .get().build();
        client.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                main.post(() -> { if (isAdded()) Toast.makeText(requireContext(), "Could not load attachment.", Toast.LENGTH_LONG).show(); });
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response res = response) {
                    if (!res.isSuccessful() || res.body() == null) {
                        main.post(() -> { if (isAdded()) Toast.makeText(requireContext(), "Attachment unavailable (HTTP " + res.code() + ").", Toast.LENGTH_LONG).show(); });
                        return;
                    }
                    if (video) {
                        // Save privately, then play within an Android VideoView dialog.
                        java.io.File temp = new java.io.File(requireContext().getCacheDir(), "report_evidence.mp4");
                        try (java.io.InputStream input = res.body().byteStream();
                             java.io.FileOutputStream output = new java.io.FileOutputStream(temp)) {
                            byte[] buffer = new byte[8192]; int n;
                            while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
                        }
                        main.post(() -> {
                            if (!isAdded()) return;
                            android.widget.VideoView player = new android.widget.VideoView(requireContext());
                            player.setVideoPath(temp.getAbsolutePath());
                            player.setMediaController(new android.widget.MediaController(requireContext()));
                            AlertDialog dialog = new AlertDialog.Builder(requireContext())
                                    .setTitle("Incident video")
                                    .setView(player)
                                    .setPositiveButton("Close", (d, w) -> player.stopPlayback())
                                    .create();
                            dialog.setOnDismissListener(d -> player.stopPlayback());
                            dialog.show();
                            player.getLayoutParams().height = dp(280);
                            player.requestLayout();
                            player.setOnPreparedListener(mp -> player.start());
                        });
                    } else {
                        byte[] bytes = res.body().bytes();
                        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        main.post(() -> {
                            if (!isAdded()) return;
                            if (bitmap == null) { Toast.makeText(requireContext(), "Could not display photo.", Toast.LENGTH_LONG).show(); return; }
                            ImageView image = new ImageView(requireContext());
                            image.setImageBitmap(bitmap);
                            image.setAdjustViewBounds(true);
                            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                            new AlertDialog.Builder(requireContext()).setTitle("Incident photo")
                                    .setView(image).setPositiveButton("Close", null).show();
                        });
                    }
                } catch (Exception e) {
                    Log.w("EduSafe", "Attachment display failed", e);
                    main.post(() -> { if (isAdded()) Toast.makeText(requireContext(), "Could not open attachment.", Toast.LENGTH_LONG).show(); });
                }
            }
        });
    }

    private void addStatusButtons(LinearLayout card, RemoteReport report) {
        if ("Verified".equals(report.status) || "False Alert".equals(report.status)) return;
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);
        if ("Received".equals(report.status)) addStatusButton(row, report, "Under Review", "Review");
        addStatusButton(row, report, "Verified", "Verify");
        addStatusButton(row, report, "False Alert", "False Alert");
        card.addView(row);
    }

    private void addStatusButton(LinearLayout row, RemoteReport report, String status, String label) {
        TextView button = new TextView(requireContext());
        button.setText(label);
        button.setTextSize(12);
        button.setTypeface(null, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(requireContext().getColor(R.color.navy));
        button.setBackgroundResource(R.drawable.bg_chip);
        button.setMinHeight(dp(44));
        button.setPadding(dp(5), dp(7), dp(5), dp(7));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1);
        params.setMargins(dp(2), 0, dp(2), 0);
        row.addView(button, params);
        button.setOnClickListener(v -> confirmStatus(report, status));
    }

    private void chooseStatus(RemoteReport report) {
        if ("Verified".equals(report.status) || "False Alert".equals(report.status)) return;
        String[] statuses = "Received".equals(report.status)
                ? new String[]{"Under Review", "Verified", "False Alert"}
                : new String[]{"Verified", "False Alert"};
        new AlertDialog.Builder(requireContext()).setTitle("Change report status")
                .setItems(statuses, (dialog, which) -> confirmStatus(report, statuses[which]))
                .show();
    }

    private void confirmStatus(RemoteReport report, String newStatus) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Mark as " + newStatus + "?")
                .setMessage("Update report " + report.trackingCode + " to " + newStatus + "?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm", (dialog, which) -> updateStatus(report, newStatus))
                .show();
    }

    private void updateStatus(RemoteReport report, String newStatus) {
        if (updating || !isAdded()) return;
        SharedPreferences prefs = requireContext().getSharedPreferences(
                "edusafe_supabase_session", Context.MODE_PRIVATE);
        String token = prefs.getString("access_token", "");
        long expiresAt = prefs.getLong("expires_at", 0);
        if (token == null || token.isEmpty() || System.currentTimeMillis() >= expiresAt) {
            showError("Session expired. Please log in again.");
            return;
        }
        updating = true;
        try {
            String code = URLEncoder.encode(report.trackingCode, "UTF-8");
            String oldStatus = URLEncoder.encode(report.status, "UTF-8");
            JSONObject body = new JSONObject().put("status", newStatus);
            Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/rest/v1/crime_reports"
                            + "?tracking_code=eq." + code + "&status=eq." + oldStatus)
                    .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Prefer", "return=representation")
                    .patch(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), body.toString()))
                    .build();
            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    main.post(() -> { updating = false; showError("Couldn't update status. Check your connection."); });
                }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try (Response res = response) {
                        int code = res.code();
                        String responseBody = res.body() == null ? "[]" : res.body().string();
                        boolean success = res.isSuccessful() && new JSONArray(responseBody).length() == 1;
                        main.post(() -> {
                            updating = false;
                            if (!isAdded()) return;
                            if (success) {
                                report.status = newStatus;
                                renderReports();
                                Toast.makeText(requireContext(), "Moved to " + newStatus, Toast.LENGTH_SHORT).show();
                            } else {
                                showError("Status not updated (HTTP " + code + "). Refresh and try again.");
                                loadReports();
                            }
                        });
                    } catch (Exception e) {
                        main.post(() -> { updating = false; showError("Couldn't read update response."); });
                    }
                }
            });
        } catch (Exception e) {
            updating = false;
            showError("Couldn't prepare status update.");
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}



























































































































































