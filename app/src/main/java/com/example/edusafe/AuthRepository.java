package com.example.edusafe;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;
import org.json.JSONArray;
import android.util.Patterns;

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

    // Temporary recovery token shared across repository instances.
    // It is kept in memory only and is cleared after successful use.
    private static String recoveryAccessToken;

    // ------------------------------------------
    // CONSTRUCTOR
    // ------------------------------------------

    public AuthRepository(Context context) {

        sessionPrefs = context.getApplicationContext()
                .getSharedPreferences(
                        "edusafe_supabase_session",
                        Context.MODE_PRIVATE
                );
    }

    // ------------------------------------------
    // SUPABASE POST REQUEST
    // ------------------------------------------

    private void request(
            String endpoint,
            JSONObject data,
            AuthCallback<JSONObject> callback) {

        RequestBody body = RequestBody.create(
                data.toString(),
                JSON
        );

        Request request = new Request.Builder()
                .url(SupabaseConfig.SUPABASE_URL + endpoint)
                .addHeader(
                        "apikey",
                        SupabaseConfig.SUPABASE_KEY
                )
                .addHeader(
                        "Content-Type",
                        "application/json"
                )
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(
                    Call call,
                    IOException e) {

                mainHandler.post(() ->
                        callback.onError(
                                "Network error: " + e.getMessage()
                        )
                );
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
                                        "Invalid server response."
                                )
                        );

                        return;
                    }

                    if (res.isSuccessful()) {

                        mainHandler.post(() ->
                                callback.onSuccess(result)
                        );

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
                                callback.onError(message)
                        );
                    }

                } catch (Exception e) {

                    mainHandler.post(() ->
                            callback.onError(
                                    "Unable to process response."
                            )
                    );
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
                    "Supabase did not return a complete session."
            );
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
    // CHECK LOGIN SESSION
    // ------------------------------------------

    public boolean hasValidSession() {

        String accessToken =
                sessionPrefs.getString(
                        "access_token",
                        ""
                );

        long expiresAt =
                sessionPrefs.getLong(
                        "expires_at",
                        0
                );

        return accessToken != null
                && !accessToken.isEmpty()
                && System.currentTimeMillis() < expiresAt;
    }

    // ------------------------------------------
    // LOG OUT
    // ------------------------------------------

    public void clearSession() {

        sessionPrefs.edit()
                .clear()
                .apply();

        recoveryAccessToken = null;
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
                    "Please use your @vossie.net email address."
            );

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
                    "Registration failed: " + e.getMessage()
            );
        }
    }

    // ------------------------------------------
    // LOGIN STUDENT
    // ------------------------------------------

    public void logIn(
            String email,
            String password,
            AuthCallback<Void> callback) {

        // Staff can use their assigned email addresses.
        // Student sign-up remains restricted to @vossie.net.
        if (email == null || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            callback.onError("Please enter a valid email address.");
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
                                        "Unable to save login session."
                                );
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
                    "Login failed: " + e.getMessage()
            );
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
                            // Student logs in separately.
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
                    "Verification failed: " + e.getMessage()
            );
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
                    "Could not resend code: " + e.getMessage()
            );
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
                    "Please use your @vossie.net email address."
            );

            return;
        }

        // Remove any previous recovery session.
        recoveryAccessToken = null;

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
                            + e.getMessage()
            );
        }
    }

    // ------------------------------------------
    // VERIFY PASSWORD RECOVERY OTP
    // ------------------------------------------

    public void verifyPasswordResetCode(
            String email,
            String code,
            AuthCallback<Void> callback) {

        recoveryAccessToken = null;

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

                            String token = result.optString(
                                    "access_token",
                                    ""
                            );

                            if (token.isEmpty()) {

                                callback.onError(
                                        "Recovery session was not returned. Please request a new code."
                                );

                                return;
                            }

                            // Store recovery token in memory.
                            // Do not store it in Intent extras.
                            recoveryAccessToken = token;

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
                            + e.getMessage()
            );
        }
    }

    // ------------------------------------------
    // CHECK RECOVERY SESSION
    // ------------------------------------------

    public boolean hasRecoverySession() {

        return recoveryAccessToken != null
                && !recoveryAccessToken.isEmpty();
    }

    // ------------------------------------------
    // UPDATE RECOVERED PASSWORD
    // ------------------------------------------

    public void updateRecoveredPassword(
            String newPassword,
            AuthCallback<Void> callback) {

        if (!hasRecoverySession()) {

            callback.onError(
                    "Recovery session is missing. Please verify your code again."
            );

            return;
        }

        if (newPassword == null
                || newPassword.length() < 8) {

            callback.onError(
                    "Password must be at least 8 characters."
            );

            return;
        }

        // Keep a local reference for this request.
        String token = recoveryAccessToken;

        try {

            JSONObject data = new JSONObject();

            data.put("password", newPassword);

            RequestBody body = RequestBody.create(
                    data.toString(),
                    JSON
            );

            Request request = new Request.Builder()
                    .url(
                            SupabaseConfig.SUPABASE_URL
                                    + "/auth/v1/user"
                    )
                    .addHeader(
                            "apikey",
                            SupabaseConfig.SUPABASE_KEY
                    )
                    .addHeader(
                            "Authorization",
                            "Bearer " + token
                    )
                    .addHeader(
                            "Content-Type",
                            "application/json"
                    )
                    .put(body)
                    .build();

            client.newCall(request).enqueue(
                    new Callback() {

                        @Override
                        public void onFailure(
                                Call call,
                                IOException e) {

                            mainHandler.post(() ->
                                    callback.onError(
                                            "Network error: "
                                                    + e.getMessage()
                                    )
                            );
                        }

                        @Override
                        public void onResponse(
                                Call call,
                                Response response)
                                throws IOException {

                            try (Response res = response) {

                                if (res.isSuccessful()) {

                                    recoveryAccessToken = null;

                                    mainHandler.post(() ->
                                            callback.onSuccess(null)
                                    );

                                } else {

                                    String message =
                                            "Could not update password. "
                                                    + "Please verify your recovery code again.";

                                    mainHandler.post(() ->
                                            callback.onError(message)
                                    );
                                }
                            }
                        }
                    }
            );

        } catch (Exception e) {

            callback.onError(
                    "Password update failed: "
                            + e.getMessage()
            );
        }
    }

    // ------------------------------------------
    // CLEAR RECOVERY SESSION
    // ------------------------------------------

    public void clearRecoverySession() {

        recoveryAccessToken = null;
    }

    // ------------------------------------------
    // OLD METHOD COMPATIBILITY
    // ------------------------------------------

    public void resetPasswordWithCode(
            String email,
            String code,
            String newPassword,
            AuthCallback<Void> callback) {

        // Do not verify the same OTP again.
        // Supabase recovery OTPs are single-use.
        // Verify the code first, then use the
        // authenticated recovery session.

        updateRecoveredPassword(
                newPassword,
                callback
        );
    }

    // ------------------------------------------
    // LOOK UP THE SIGNED-IN USER'S ROLE
    // ------------------------------------------

    public void getUserRole(AuthCallback<String> callback) {

        String accessToken = sessionPrefs.getString("access_token", "");
        if (accessToken == null || accessToken.isEmpty() || !hasValidSession()) {
            mainHandler.post(() -> callback.onError("Your session has expired. Please log in again."));
            return;
        }

        // RLS limits this query to the current user's own role.
        // No user ID is accepted from the caller.
        Request request = new Request.Builder()
                .url(SupabaseConfig.SUPABASE_URL
                        + "/rest/v1/user_roles?select=role,is_banned")
                .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError(
                        "Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response res = response) {
                    String responseText = res.body() != null
                            ? res.body().string() : "";

                    if (!res.isSuccessful()) {
                        mainHandler.post(() -> callback.onError(
                                "Unable to retrieve your account role (HTTP "
                                        + res.code() + ")."));
                        return;
                    }

                    JSONArray roles = new JSONArray(responseText);
                    if (roles.length() != 1) {
                        mainHandler.post(() -> callback.onError(
                                "No unique account role was found. Please contact support."));
                        return;
                    }

                    boolean isBanned = roles.getJSONObject(0).optBoolean("is_banned", false);
                    if (isBanned) {
                        mainHandler.post(() -> callback.onError(
                                "Your EduSafe account has been banned. Please contact the administrator."));
                        return;
                    }

                    String role = roles.getJSONObject(0).optString("role", "");
                    if (!"student".equals(role)
                            && !"admin".equals(role)
                            && !"security".equals(role)) {
                        mainHandler.post(() -> callback.onError(
                                "Your account has an invalid role."));
                        return;
                    }

                    mainHandler.post(() -> callback.onSuccess(role));
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError(
                            "Unable to read your account role."));
                }
            }
        });
    }
}



