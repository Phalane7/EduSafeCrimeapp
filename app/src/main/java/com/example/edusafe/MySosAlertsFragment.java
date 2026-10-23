package com.example.edusafe;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MySosAlertsFragment extends Fragment {
    private final OkHttpClient client = new OkHttpClient();
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout list;
    private TextView message;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
                                                 @Nullable ViewGroup container, @Nullable Bundle state) {
        View root = inflater.inflate(R.layout.fragment_my_sos_alerts, container, false);
        list = root.findViewById(R.id.sosHistoryContainer);
        message = root.findViewById(R.id.tvSosHistoryMessage);
        return root;
    }

    @Override public void onResume() {
        super.onResume();
        loadAlerts();
    }

    private void loadAlerts() {
        if (!isAdded() || list == null) return;
        list.removeAllViews();
        message.setVisibility(View.VISIBLE);
        message.setText("Loading your SOS alerts...");
        SharedPreferences prefs = requireContext().getSharedPreferences("edusafe_supabase_session", Context.MODE_PRIVATE);
        String token = prefs.getString("access_token", "");
        if (token == null || token.isEmpty()) {
            message.setText("Please log in to view your SOS alerts.");
            return;
        }
        // Identify the authenticated user rather than trusting a local profile name or email.
        Request userRequest = new Request.Builder()
                .url(SupabaseConfig.SUPABASE_URL + "/auth/v1/user")
                .header("apikey", SupabaseConfig.SUPABASE_KEY)
                .header("Authorization", "Bearer " + token).get().build();
        client.newCall(userRequest).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                showMessage("Could not connect. Please try again.");
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response res = response) {
                    if (!res.isSuccessful() || res.body() == null) {
                        showMessage("Session expired. Please log in again."); return;
                    }
                    String userId = new JSONObject(res.body().string()).optString("id", "");
                    if (userId.isEmpty()) { showMessage("Unable to identify your account."); return; }
                    fetchAlerts(token, userId);
                } catch (Exception e) { showMessage("Could not read account details."); }
            }
        });
    }

    private void fetchAlerts(String token, String userId) {
        try {
            String url = SupabaseConfig.SUPABASE_URL + "/rest/v1/sos_alerts"
                    + "?select=id,status,created_at,arrived_at,resolved_at,verified&user_id=eq." + URLEncoder.encode(userId, "UTF-8")
                    + "&order=created_at.desc&limit=100";
            Request request = new Request.Builder().url(url)
                    .header("apikey", SupabaseConfig.SUPABASE_KEY)
                    .header("Authorization", "Bearer " + token).get().build();
            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    showMessage("Could not load SOS alerts. Check your connection.");
                }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try (Response res = response) {
                        if (!res.isSuccessful() || res.body() == null) {
                            showMessage("Unable to load alerts (HTTP " + res.code() + ")."); return;
                        }
                        JSONArray alerts = new JSONArray(res.body().string());
                        main.post(() -> render(alerts));
                    } catch (Exception e) { showMessage("Could not read SOS alerts."); }
                }
            });
        } catch (Exception e) { showMessage("Could not prepare SOS history request."); }
    }

    private void render(JSONArray alerts) {
        if (!isAdded() || list == null) return;
        list.removeAllViews();
        if (alerts.length() == 0) {
            message.setVisibility(View.VISIBLE);
            message.setText("You have not activated any SOS alerts yet.");
            return;
        }
        message.setVisibility(View.GONE);
        for (int i = 0; i < alerts.length(); i++) {
            JSONObject alert = alerts.optJSONObject(i);
            if (alert == null) continue;
            String status = alert.optString("status", "Unknown");
            boolean verified = alert.optBoolean("verified", false);
            boolean negative = "Cancelled".equalsIgnoreCase(status) || "False Alert".equalsIgnoreCase(status);
            boolean positive = "Resolved".equalsIgnoreCase(status) || "Verified".equalsIgnoreCase(status) || verified;
            int badgeColor = negative ? Color.rgb(178, 31, 45) : positive ? Color.rgb(3, 117, 87) : Color.rgb(27, 73, 151);
            int badgeBg = negative ? Color.rgb(255, 230, 235) : positive ? Color.rgb(216, 245, 234) : Color.rgb(224, 235, 255);

            LinearLayout card = new LinearLayout(requireContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(17), dp(16), dp(17), dp(16));
            card.setBackgroundResource(R.drawable.edusafe_student_sos_history_card);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
            cardLp.bottomMargin = dp(12);
            list.addView(card, cardLp);

            LinearLayout top = new LinearLayout(requireContext());
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(top);
            TextView title = new TextView(requireContext());
            title.setText(String.format(Locale.getDefault(), "SOS Alert #%03d", i + 1));
            title.setTextSize(15);
            title.setTypeface(null, Typeface.BOLD);
            title.setTextColor(Color.rgb(10, 30, 75));
            top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            TextView badge = new TextView(requireContext());
            badge.setText(status + (verified && !"Verified".equalsIgnoreCase(status) ? " · Verified" : ""));
            badge.setTextSize(11);
            badge.setTypeface(null, Typeface.BOLD);
            badge.setTextColor(badgeColor);
            badge.setPadding(dp(10), dp(5), dp(10), dp(5));
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(badgeBg);
            pill.setCornerRadius(dp(50));
            badge.setBackground(pill);
            top.addView(badge);

            String created = formatDate(alert.optString("created_at", ""));
            addDetail(card, created, 8);
            String arrived = alert.optString("arrived_at", "");
            if (arrived != null && !arrived.isEmpty() && !"null".equalsIgnoreCase(arrived)) {
                try {
                    long startMillis = Instant.parse(alert.optString("created_at")).toEpochMilli();
                    long endMillis = Instant.parse(arrived).toEpochMilli();
                    long seconds = Math.max(0, (endMillis - startMillis) / 1000);
                    addDetail(card, String.format(Locale.getDefault(), "Security response: %02d:%02d", seconds / 60, seconds % 60), 5);
                } catch (Exception ignored) { }
            }
            String resolved = alert.optString("resolved_at", "");
            if (resolved != null && !resolved.isEmpty() && !"null".equalsIgnoreCase(resolved)) {
                addDetail(card, "Resolved: " + formatDate(resolved), 5);
            }
        }
    }

    private String formatDate(String raw) {
        try {
            long millis = Instant.parse(raw).toEpochMilli();
            return new SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()).format(new Date(millis));
        } catch (Exception ignored) { return raw == null ? "" : raw; }
    }

    private void addDetail(LinearLayout parent, String value, int topMargin) {
        TextView text = new TextView(requireContext());
        text.setText(value);
        text.setTextSize(12);
        text.setTextColor(Color.rgb(89, 108, 143));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(topMargin);
        parent.addView(text, lp);
    }

    private void showMessage(String value) {
        main.post(() -> {
            if (isAdded() && message != null) {
                message.setVisibility(View.VISIBLE);
                message.setText(value);
            }
        });
    }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density); }
    @Override public void onDestroyView() {
        list = null;
        message = null;
        super.onDestroyView();
    }
}


