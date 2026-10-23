package com.example.edusafe.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import androidx.annotation.NonNull;

import com.example.edusafe.SupabaseConfig;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class SosVoiceStorage {

    public interface Result {
        void done(boolean ok, String message);
    }

    private static final OkHttpClient CLIENT = new OkHttpClient();
    private static final Handler MAIN =
            new Handler(Looper.getMainLooper());

    private static final MediaType AUDIO =
            MediaType.parse("audio/mp4");

    private static final MediaType JSON =
            MediaType.parse("application/json; charset=utf-8");

    private SosVoiceStorage() {
    }

    public static void upload(
            Context context,
            String sosId,
            File audio,
            Result result) {

        SharedPreferences prefs = context.getSharedPreferences(
                "edusafe_supabase_session",
                Context.MODE_PRIVATE
        );

        String token = prefs.getString("access_token", "");

        if (token == null || token.isEmpty()
                || System.currentTimeMillis()
                >= prefs.getLong("expires_at", 0)) {

            sendResult(result, false,
                    "Please log in again to send your recording.");
            return;
        }

        if (sosId == null || sosId.trim().isEmpty()) {
            sendResult(result, false,
                    "SOS alert ID is missing.");
            return;
        }

        if (audio == null || !audio.isFile()
                || audio.length() == 0) {

            sendResult(result, false,
                    "Recording file is empty.");
            return;
        }

        // Keep the same storage path when retrying an upload.
        String path = prefs.getString(
                "voice_upload_path_" + sosId, ""
        );

        if (path.isEmpty()) {
            try {
                String[] tokenParts = token.split("\\.");

                if (tokenParts.length < 2) {
                    throw new IllegalArgumentException(
                            "Invalid session token"
                    );
                }

                byte[] decoded = Base64.decode(
                        tokenParts[1],
                        Base64.URL_SAFE
                                | Base64.NO_WRAP
                                | Base64.NO_PADDING
                );

                String userId = new JSONObject(
                        new String(
                                decoded,
                                java.nio.charset.StandardCharsets.UTF_8
                        )
                ).getString("sub");

                path = userId + "/"
                        + sosId + "/"
                        + UUID.randomUUID() + ".m4a";

                prefs.edit()
                        .putString(
                                "voice_upload_path_" + sosId,
                                path
                        )
                        .apply();

            } catch (Exception e) {
                sendResult(result, false,
                        "Could not identify the signed-in student.");
                return;
            }
        }

        final String storagePath = path;

        // Upload the voice recording to Supabase Storage.
        Request uploadRequest = new Request.Builder()
                .url(
                        SupabaseConfig.SUPABASE_URL
                                + "/storage/v1/object/sos-voice/"
                                + storagePath
                )
                .header(
                        "apikey",
                        SupabaseConfig.SUPABASE_KEY
                )
                .header(
                        "Authorization",
                        "Bearer " + token
                )
                .header("x-upsert", "true")
                .post(RequestBody.create(AUDIO, audio))
                .build();

        CLIENT.newCall(uploadRequest).enqueue(new Callback() {

            @Override
            public void onFailure(
                    @NonNull Call call,
                    @NonNull IOException e) {

                sendResult(
                        result,
                        false,
                        "Upload failed. Tap retry."
                );
            }

            @Override
            public void onResponse(
                    @NonNull Call call,
                    @NonNull Response response) throws IOException {

                try (Response uploadResponse = response) {

                    if (!uploadResponse.isSuccessful()) {

                        sendResult(
                                result,
                                false,
                                "Audio upload failed (HTTP "
                                        + uploadResponse.code()
                                        + ")."
                        );
                        return;
                    }
                }

                // Link the recording to its SOS alert.
                try {
                    JSONObject body = new JSONObject();

                    body.put("sos_id", sosId);
                    body.put("storage_path", storagePath);

                    Request insertRequest = new Request.Builder()
                            .url(
                                    SupabaseConfig.SUPABASE_URL
                                            + "/rest/v1/sos_voice_recordings"
                                            + "?on_conflict=sos_id"
                            )
                            .header(
                                    "apikey",
                                    SupabaseConfig.SUPABASE_KEY
                            )
                            .header(
                                    "Authorization",
                                    "Bearer " + token
                            )
                            .header(
                                    "Prefer",
                                    "resolution=merge-duplicates,return=minimal"
                            )
                            .post(
                                    RequestBody.create(
                                            JSON,
                                            body.toString()
                                    )
                            )
                            .build();

                    CLIENT.newCall(insertRequest).enqueue(
                            new Callback() {

                                @Override
                                public void onFailure(
                                        @NonNull Call call,
                                        @NonNull IOException e) {

                                    sendResult(
                                            result,
                                            false,
                                            "Recording saved but linking failed. Tap retry."
                                    );
                                }

                                @Override
                                public void onResponse(
                                        @NonNull Call call,
                                        @NonNull Response response) {

                                    try (Response insertResponse = response) {

                                        if (insertResponse.isSuccessful()) {

                                            prefs.edit()
                                                    .remove(
                                                            "voice_upload_path_" + sosId
                                                    )
                                                    .apply();

                                            sendResult(
                                                    result,
                                                    true,
                                                    "Recording sent to security."
                                            );

                                        } else {

                                            sendResult(
                                                    result,
                                                    false,
                                                    "Could not link recording (HTTP "
                                                            + insertResponse.code()
                                                            + "). Tap retry."
                                            );
                                        }
                                    }
                                }
                            }
                    );

                } catch (Exception e) {

                    sendResult(
                            result,
                            false,
                            "Could not prepare recording details."
                    );
                }
            }
        });
    }

    // Send the result back to the main Android thread.
    private static void sendResult(
            Result result,
            boolean ok,
            String message) {

        MAIN.post(() -> {
            if (result != null) {
                result.done(ok, message);
            }
        });
    }
}