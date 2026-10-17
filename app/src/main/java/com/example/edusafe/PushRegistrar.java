package com.example.edusafe;

import android.content.Context;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

// Gets this phone's push-notification token and gives it to the server
public class PushRegistrar {
    private static final String TAG = "EduSafe";

    public static void init(Context ctx) {
        final Context app = ctx.getApplicationContext();
        try {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                if (!task.isSuccessful() || task.getResult() == null) return;
                String token = task.getResult();
                Session.putString(app, "fcm_token", token);
                if (!token.equals(Session.getString(app, "fcm_sent"))) send(app, token);
            });
        } catch (Exception e) {
            Log.w(TAG, "Push setup skipped", e);
        }
    }

    public static void send(final Context app, final String token) {
        Net.bg(() -> {
            try {
                ApiClient.Result r = ApiClient.send(app, "POST", "/devices", new JSONObject().put("token", token));
                if (r.ok()) Session.putString(app, "fcm_sent", token);
            } catch (Exception e) {
                Log.w(TAG, "Could not register device", e);
            }
        });
    }
}
