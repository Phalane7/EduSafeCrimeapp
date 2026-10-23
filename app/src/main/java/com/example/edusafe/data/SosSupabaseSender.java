package com.example.edusafe.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.example.edusafe.SupabaseConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Creates an SOS alert for Security; SMS is handled separately. */
public final class SosSupabaseSender {
    public interface ResultCallback {
        /** Success means Supabase saved the alert, not that Security has responded. */
        void onResult(boolean success, String message);
    }

    public interface AlertResultCallback {
        /** alertId is null when saving fails. Called on the main thread. */
        void onResult(boolean success, String message, String alertId);
    }

    public static final String ACTIVE_SOS_PREFS = "edusafe_active_sos";
    public static final String KEY_ALERT_ID = "alert_id";
    public static final String KEY_CREATED_AT = "created_at";
    public static final String KEY_SESSION_TOKEN = "session_token";

    private static final OkHttpClient CLIENT = new OkHttpClient();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private SosSupabaseSender() { }

    /** Existing API, preserved for SosActiveActivity. */
    public static void send(Context context, ResultCallback callback) {
        sendWithId(context, (success, message, alertId) -> {
            if (callback != null) callback.onResult(success, message);
        });
    }

    /** New API also returns the ID of the saved emergency. */
    public static void sendWithId(Context context, AlertResultCallback callback) {
        Context app = context.getApplicationContext();
        SharedPreferences session = app.getSharedPreferences(
                "edusafe_supabase_session", Context.MODE_PRIVATE);
        String token = session.getString("access_token", "");
        long expiry = session.getLong("expires_at", 0L);
        if (token == null || token.isEmpty() || System.currentTimeMillis() >= expiry) {
            notifyResult(callback, false,
                    "Session expired. Please log in again to send SOS to security.", null);
            return;
        }

        AtomicBoolean completed = new AtomicBoolean(false);
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable timeout = () -> {
            if (completed.compareAndSet(false, true)) {
                post(app, token, null, callback);
            }
        };
        handler.postDelayed(timeout, 3000L);
        try {
            new LocationHelper(app).getCurrentLocation(location -> {
                if (!completed.compareAndSet(false, true)) return;
                handler.removeCallbacks(timeout);
                post(app, token, location, callback);
            });
        } catch (Exception e) {
            if (completed.compareAndSet(false, true)) {
                handler.removeCallbacks(timeout);
                post(app, token, null, callback);
            }
        }
    }

    private static void post(Context app, String token, Location location,
                             AlertResultCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("status", "Active");
            if (location != null) {
                body.put("latitude", location.getLatitude());
                body.put("longitude", location.getLongitude());
            }
            Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/rest/v1/sos_alerts")
                    .header("apikey", SupabaseConfig.SUPABASE_KEY)
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=representation")
                    .post(RequestBody.create(JSON, body.toString()))
                    .build();
            CLIENT.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    notifyResult(callback, false,
                            "Couldn't reach Supabase. SOS was not confirmed on Security Dashboard.", null);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try (Response res = response) {
                        if (!res.isSuccessful()) {
                            notifyResult(callback, false,
                                    "SOS not saved to Security Dashboard (HTTP " + res.code() + ").", null);
                            return;
                        }
                        JSONArray saved = new JSONArray(res.body() == null ? "[]" : res.body().string());
                        if (saved.length() != 1) {
                            notifyResult(callback, false,
                                    "SOS response was incomplete. Please check Security Dashboard.", null);
                            return;
                        }
                        JSONObject alert = saved.getJSONObject(0);
                        String id = alert.optString("id", "");
                        String createdAt = alert.optString("created_at", "");
                        if (id.isEmpty()) {
                            notifyResult(callback, false,
                                    "SOS was accepted but its ID was missing. Please check Security Dashboard.", null);
                            return;
                        }
                        // Store the last saved ID as a convenience. Future screen code MUST
                        // verify the alert's current status with Supabase, and must not
                        // rely on this local preference as the source of truth.
                        SharedPreferences currentSession = app.getSharedPreferences(
                                "edusafe_supabase_session", Context.MODE_PRIVATE);
                        String currentToken = currentSession.getString("access_token", "");
                        if (token.equals(currentToken)) {
                            app.getSharedPreferences(ACTIVE_SOS_PREFS, Context.MODE_PRIVATE)
                                    .edit()
                                    .putString(KEY_ALERT_ID, id)
                                    .putString(KEY_CREATED_AT, createdAt)
                                    .apply();
                        }
                        notifyResult(callback, true, "SOS sent to Security Dashboard.", id);
                    } catch (Exception e) {
                        notifyResult(callback, false,
                                "SOS response couldn't be read. Please check Security Dashboard.", null);
                    }
                }
            });
        } catch (Exception e) {
            notifyResult(callback, false,
                    "Couldn't prepare the SOS for Security Dashboard.", null);
        }
    }

    private static void notifyResult(AlertResultCallback callback, boolean success,
                                     String message, String id) {
        if (callback != null) MAIN.post(() -> callback.onResult(success, message, id));
    }
}

