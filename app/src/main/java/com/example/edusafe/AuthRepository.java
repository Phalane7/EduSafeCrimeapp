package com.example.edusafe;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AuthRepository {

    private static final MediaType JSON =
            MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient();

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private final SharedPreferences sessionPrefs;

    // Constructor
    public AuthRepository(Context context) {
        sessionPrefs = context.getApplicationContext()
                .getSharedPreferences(
                        "edusafe_supabase_session",
                        Context.MODE_PRIVATE
                );
    }

    // ------------------------------------------
    // SUPABASE API REQUEST
    // ------------------------------------------

    private void request(
            String endpoint,
            JSONObject data,
            AuthCallback<JSONObject> callback) {

        RequestBody body = RequestBody.create(
                data.toString(), JSON);

        Request request = new Request.Builder()
                .url(SupabaseConfig.SUPABASE_URL + endpoint)
                .addHeader(
                        "apikey",
                        SupabaseConfig.SUPABASE_KEY)
                .addHeader(
                        "Content-Type",
                        "application/json")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {

                mainHandler.post(() ->
                        callback.onError(
                                "Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(
                    Call call,
                    Response response) throws IOException {

                try (Response res = response) {

                    String text = res.body() != null
                            ? res.body().string()
                            : "";

                    JSONObject result;

                    try {
                        result = text.isEmpty()
                                ? new JSONObject()
                                : new JSONObject(text);
                    } catch (Exception e) {
                        mainHandler.post(() ->
                                callback.onError(
                                        "Invalid server response."));
                        return;
                    }

                    if (res.isSuccessful()) {

                        mainHandler.post(() ->
                                callback.onSuccess(result));

                    } else {

                        String message = result.optString(
                                "msg",
                                result.optString(
                                        "error_description",
                                        result.optString(
                                                "message",
                                                result.optString(
                                                        "error",
                                                        "Request failed."
                                                )
                                        )
                                )
                        );

                        mainHandler.post(() ->
                                callback.onError(message));
                    }

                } catch (Exception e) {

                    mainHandler.post(() ->
                            callback.onError(
                                    "Unable to process response."));
                }
            }
        });
    }

    // ------------------------------------------
    // SAVE LOGIN SESSION
    // ------------------------------------------

    private void saveSession(JSONObject result) {

        String accessToken =
                result.optString("access_token", "");

        String refreshToken =
                result.optString("refresh_token", "");

        long expiresIn =
                result.optLong("expires_in", 0);

        if (accessToken.isEmpty()
                || refreshToken.isEmpty()
                || expiresIn <= 0) {

            throw new IllegalStateException(
                    "Supabase did not return a complete session.");
        }

        long expiresAt =
                System.currentTimeMillis()
                        + expiresIn * 1000L;

        sessionPrefs.edit()
                .putString("access_token", accessToken)
                .putString("refresh_token", refreshToken)
                .putLong("expires_at", expiresAt)
                .apply();
    }

    // ------------------------------------------
    // CHECK SESSION
    // ------------------------------------------

    public boolean hasValidSession() {

        String accessToken =
                sessionPrefs.getString(
                        "access_token", "");

        long expiresAt =
                sessionPrefs.getLong(
                        "expires_at", 0);

        return accessToken != null
                && !accessToken.isEmpty()
                && System.currentTimeMillis() < expiresAt;
    }

    // ------------------------------------------
    // LOG OUT / CLEAR SESSION
    // ------------------------------------------

    public void clearSession() {
        sessionPrefs.edit().clear().apply();
    }

    // ------------------------------------------
    // REGISTER STUDENT
    // ------------------------------------------

    public void signUp(
            String firstName,
            String surname,
            String phone,
            String email,
            String password,
            AuthCallback<Void> callback) {

        if (!EmailValidator.isValidVossieEmail(email)) {

            callback.onError(
                    "Please use your @vossie.net email address.");
            return;
        }

        try {

            JSONObject data = new JSONObject();

            data.put("email", email);
            data.put("password", password);

            JSONObject metadata = new JSONObject();

            metadata.put("first_name", firstName);
            metadata.put("surname", surname);
            metadata.put("phone", phone);

            data.put("data", metadata);

            request(
                    "/auth/v1/signup",
                    data,
                    new AuthCallback<JSONObject>() {

                        @Override
                        public void onSuccess(JSONObject result) {
                            callback.onSuccess(null);
                        }

                        @Override
                        public void onError(String message) {
                            callback.onError(message);
                        }
                    }
            );

        } catch (Exception e) {

            callback.onError(
                    "Registration failed: " + e.getMessage());
        }
    }

    // ------------------------------------------
    // LOGIN STUDENT
    // ------------------------------------------

    public void logIn(
            String email,
            String password,
            AuthCallback<Void> callback) {

        if (!EmailValidator.isValidVossieEmail(email)) {

            callback.onError(
                    "Please use your @vossie.net email address.");
            return;
        }

        try {

            JSONObject data = new JSONObject();

            data.put("email", email);
            data.put("password", password);

            request(
                    "/auth/v1/token?grant_type=password",
                    data,
                    new AuthCallback<JSONObject>() {

                        @Override
                        public void onSuccess(JSONObject result) {

                            try {

                                saveSession(result);

                                callback.onSuccess(null);

                            } catch (Exception e) {

                                callback.onError(
                                        "Unable to save login session.");
                            }
                        }

                        @Override
                        public void onError(String message) {

                            callback.onError(message);
                        }
                    }
            );

        } catch (Exception e) {

            callback.onError(
                    "Login failed: " + e.getMessage());
        }
    }

    // ------------------------------------------
    // VERIFY REGISTRATION OTP
    // ------------------------------------------

    public void verifySignUpCode(
            String email,
            String code,
            AuthCallback<Void> callback) {

        try {

            JSONObject data = new JSONObject();

            data.put("email", email);
            data.put("token", code);
            data.put("type", "signup");

            request(
                    "/auth/v1/verify",
                    data,
                    new AuthCallback<JSONObject>() {

                        @Override
                        public void onSuccess(JSONObject result) {

                            // Registration verified.
                            // Student will log in separately.
                            callback.onSuccess(null);
                        }

                        @Override
                        public void onError(String message) {

                            callback.onError(message);
                        }
                    }
            );

        } catch (Exception e) {

            callback.onError(
                    "Verification failed: " + e.getMessage());
        }
    }

    // ------------------------------------------
    // RESEND REGISTRATION OTP
    // ------------------------------------------

    public void resendSignUpCode(
            String email,
            AuthCallback<Void> callback) {

        try {

            JSONObject data = new JSONObject();

            data.put("email", email);
            data.put("type", "signup");

            request(
                    "/auth/v1/resend",
                    data,
                    new AuthCallback<JSONObject>() {

                        @Override
                        public void onSuccess(JSONObject result) {

                            callback.onSuccess(null);
                        }

                        @Override
                        public void onError(String message) {

                            callback.onError(message);
                        }
                    }
            );

        } catch (Exception e) {

            callback.onError(
                    "Could not resend code: " + e.getMessage());
        }
    }

    // ------------------------------------------
    // SEND PASSWORD RESET OTP
    // ------------------------------------------

    public void sendPasswordResetCode(
            String email,
            AuthCallback<Void> callback) {

        if (!EmailValidator.isValidVossieEmail(email)) {

            callback.onError(
                    "Please use your @vossie.net email address.");
            return;
        }

        try {

            JSONObject data = new JSONObject();

            data.put("email", email);

            request(
                    "/auth/v1/recover",
                    data,
                    new AuthCallback<JSONObject>() {

                        @Override
                        public void onSuccess(JSONObject result) {

                            callback.onSuccess(null);
                        }

                        @Override
                        public void onError(String message) {

                            callback.onError(message);
                        }
                    }
            );

        } catch (Exception e) {

            callback.onError(
                    "Password reset request failed: "
                            + e.getMessage());
        }
    }

    // ------------------------------------------
    // VERIFY PASSWORD RESET OTP
    // ------------------------------------------

    public void verifyPasswordResetCode(
            String email,
            String code,
            AuthCallback<Void> callback) {

        try {

            JSONObject data = new JSONObject();

            data.put("email", email);
            data.put("token", code);
            data.put("type", "recovery");

            request(
                    "/auth/v1/verify",
                    data,
                    new AuthCallback<JSONObject>() {

                        @Override
                        public void onSuccess(JSONObject result) {

                            // A successful response contains a
                            // recovery session. The next step
                            // will securely handle that session.
                            callback.onSuccess(null);
                        }

                        @Override
                        public void onError(String message) {

                            callback.onError(message);
                        }
                    }
            );

        } catch (Exception e) {

            callback.onError(
                    "Password reset verification failed: "
                            + e.getMessage());
        }
    }

    // ------------------------------------------
    // UPDATE PASSWORD - NOT YET IMPLEMENTED
    // ------------------------------------------

    public void resetPasswordWithCode(
            String email,
            String code,
            String newPassword,
            AuthCallback<Void> callback) {

        // Supabase requires an authenticated recovery
        // session to update the user's password.
        // We will implement this securely in the
        // password-reset migration step.

        callback.onError(
                "Password reset setup is not complete yet.");
    }
}
