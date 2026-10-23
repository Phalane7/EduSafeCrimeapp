package com.example.edusafe.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.example.edusafe.SupabaseConfig;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Cancels only the signed-in student's own active SOS via a restricted Supabase RPC. */
public final class SosCancellation {
    public interface ResultCallback {
        void onResult(boolean success, String message);
    }

    private static final OkHttpClient CLIENT = new OkHttpClient();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private SosCancellation() {}

    public static void cancel(Context context, String alertId, ResultCallback callback) {
        Context app = context.getApplicationContext();
        SharedPreferences session = app.getSharedPreferences(
                "edusafe_supabase_session", Context.MODE_PRIVATE);
        String token = session.getString("access_token", "");
        long expiry = session.getLong("expires_at", 0L);
        if (token == null || token.isEmpty() || System.currentTimeMillis() >= expiry) {
            deliver(callback, false, "Your session expired. Please log in again.");
            return;
        }
        if (alertId == null || alertId.trim().isEmpty()) {
            deliver(callback, false, "No SOS alert ID is available.");
            return;
        }
        try {
            JSONObject payload = new JSONObject();
            payload.put("p_alert_id", alertId);
            Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/rest/v1/rpc/cancel_my_sos")
                    .header("apikey", SupabaseConfig.SUPABASE_KEY)
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(JSON, payload.toString()))
                    .build();

            CLIENT.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    deliver(callback, false, "Network error: " + e.getClass().getSimpleName());
                }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try (Response res = response) {
                        if (!res.isSuccessful()) {
                            deliver(callback, false, "Supabase rejected cancellation (HTTP " + res.code() + ").");
                            return;
                        }
                        String body = res.body() == null ? "" : res.body().string().trim();
                        if (!"true".equals(body)) {
                            deliver(callback, false, "SOS was not cancelled. It may already be closed or is not your alert.");
                            return;
                        }
                        SharedPreferences active = app.getSharedPreferences(
                                SosSupabaseSender.ACTIVE_SOS_PREFS, Context.MODE_PRIVATE);
                        if (token.equals(session.getString("access_token", "")) &&
                                alertId.equals(active.getString(SosSupabaseSender.KEY_ALERT_ID, ""))) {
                            active.edit()
                                    .remove(SosSupabaseSender.KEY_ALERT_ID)
                                    .remove(SosSupabaseSender.KEY_CREATED_AT)
                                    .apply();
                        }
                        deliver(callback, true, "SOS cancelled.");
                    } catch (Exception e) {
                        deliver(callback, false, "Could not read cancellation response.");
                    }
                }
            });
        } catch (Exception e) {
            deliver(callback, false, "Could not prepare cancellation request.");
        }
    }

    private static void deliver(ResultCallback callback, boolean success, String message) {
        if (callback != null) MAIN.post(() -> callback.onResult(success, message));
    }
}
