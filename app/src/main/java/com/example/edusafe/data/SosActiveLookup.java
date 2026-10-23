package com.example.edusafe.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.example.edusafe.SupabaseConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Reads the signed-in student's active SOS from Supabase (RLS enforces ownership). */
public final class SosActiveLookup {
    public interface ResultCallback {
        /** Called on the main thread. error is null on a successful lookup, even if alert is null. */
        void onResult(Alert alert, String error);
    }

    public static final class Alert {
        public final String id;
        public final String status;
        public final String createdAt;
        public final String arrivedAt;

        public Alert(String id, String status, String createdAt) {
            this(id, status, createdAt, null);
        }

        public Alert(String id, String status, String createdAt, String arrivedAt) {
            this.id = id;
            this.status = status;
            this.createdAt = createdAt;
            this.arrivedAt = arrivedAt;
        }
    }

    private static final OkHttpClient CLIENT = new OkHttpClient();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String ACTIVE_STATUSES = "status=in.(Active,Under%20Review,On%20the%20Way,Arrived)";

    private SosActiveLookup() { }

    public static void findActive(Context context, ResultCallback callback) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences("edusafe_supabase_session", Context.MODE_PRIVATE);
        String token = prefs.getString("access_token", "");
        long expiry = prefs.getLong("expires_at", 0L);
        if (token == null || token.isEmpty() || System.currentTimeMillis() >= expiry) {
            deliver(callback, null, "Session expired. Please sign in again.");
            return;
        }

        // Student SELECT RLS allows only their own SOS records. Do not rely on a local cached ID.
        String url = SupabaseConfig.SUPABASE_URL + "/rest/v1/sos_alerts"
                + "?select=id,status,created_at,arrived_at&" + ACTIVE_STATUSES
                + "&order=created_at.desc&limit=1";
        Request request = new Request.Builder()
                .url(url)
                .header("apikey", SupabaseConfig.SUPABASE_KEY)
                .header("Authorization", "Bearer " + token)
                .get()
                .build();
        CLIENT.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                deliver(callback, null, "Unable to check Live SOS. Check your internet connection.");
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response res = response) {
                    if (!res.isSuccessful()) {
                        deliver(callback, null, "Live SOS lookup failed (HTTP " + res.code() + ").");
                        return;
                    }
                    JSONArray rows = new JSONArray(res.body() == null ? "[]" : res.body().string());
                    if (rows.length() == 0) {
                        deliver(callback, null, null);
                        return;
                    }
                    JSONObject row = rows.getJSONObject(0);
                    String id = row.optString("id", "");
                    if (id.isEmpty()) {
                        deliver(callback, null, "Live SOS returned an invalid alert ID.");
                        return;
                    }
                    deliver(callback, new Alert(id, row.optString("status", "Active"),
                            row.optString("created_at", ""), row.optString("arrived_at", "")), null);
                } catch (Exception e) {
                    deliver(callback, null, "Unable to read the Live SOS response.");
                }
            }
        });
    }

    private static void deliver(ResultCallback callback, Alert alert, String error) {
        if (callback != null) MAIN.post(() -> callback.onResult(alert, error));
    }
}



