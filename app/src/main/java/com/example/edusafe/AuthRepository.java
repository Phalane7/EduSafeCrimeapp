package com.example.edusafe;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.auth.ActionCodeSettings;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuthRepository {

    private final FirebaseAuth auth =
            FirebaseAuth.getInstance();

    private final FirebaseFirestore db =
            FirebaseFirestore.getInstance();

    // ---------------------------------------------------------
    // CLOUDFLARE WORKER
    // ---------------------------------------------------------

    private static final String OTP_WORKER_URL =
            "https://edusafe-otp.serokaphalane.workers.dev/";

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    // ---------------------------------------------------------
    // SIGN UP
    // ---------------------------------------------------------

    public void signUp(
            String firstName,
            String surname,
            String phone,
            String email,
            String password,
            AuthCallback<Void> callback
    ) {

        email = email.trim().toLowerCase();

        if (!EmailValidator.isValidVossieEmail(email)) {

            callback.onError(
                    "Invalid email. Please use your @vossie.net address."
            );

            return;
        }

        String finalEmail = email;

        auth.createUserWithEmailAndPassword(
                        finalEmail,
                        password
                )
                .addOnSuccessListener(authResult -> {

                    FirebaseUser user =
                            authResult.getUser();

                    if (user == null) {

                        callback.onError(
                                "Account was not created. Please try again."
                        );

                        return;
                    }

                    // -------------------------------------------------
                    // IMPORTANT:
                    // The Firebase account now exists.
                    //
                    // Tell SignUpActivity immediately.
                    //
                    // DO NOT wait for Firestore.
                    // DO NOT wait for OTP.
                    // -------------------------------------------------

                    callback.onSuccess(null);

                    // -------------------------------------------------
                    // SAVE USER PROFILE IN THE BACKGROUND
                    // -------------------------------------------------

                    Map<String, Object> profile =
                            new HashMap<>();

                    profile.put(
                            "firstName",
                            firstName
                    );

                    profile.put(
                            "surname",
                            surname
                    );

                    profile.put(
                            "phone",
                            phone
                    );

                    profile.put(
                            "email",
                            finalEmail
                    );

                    profile.put(
                            "verified",
                            false
                    );

                    profile.put(
                            "banned",
                            false
                    );

                    db.collection("users")
                            .document(user.getUid())
                            .set(profile)
                            .addOnFailureListener(e -> Log.e(
                                    "AuthRepository",
                                    "Could not save user profile",
                                    e
                            ));

                })
                .addOnFailureListener(e -> callback.onError(
                        e.getMessage() != null
                                ? e.getMessage()
                                : "Could not create your account."
                ));
    }

    // ---------------------------------------------------------
    // SEND REGISTRATION OTP
    // ---------------------------------------------------------

    public void sendRegistrationCode(
            String email,
            AuthCallback<Void> callback
    ) {

        email = email.trim().toLowerCase();

        if (!EmailValidator.isValidVossieEmail(email)) {

            callback.onError(
                    "Invalid email. Please use your @vossie.net address."
            );

            return;
        }

        String finalEmail = email;

        executor.execute(() -> {

            try {

                JSONObject requestBody =
                        new JSONObject();

                requestBody.put(
                        "action",
                        "send"
                );

                requestBody.put(
                        "email",
                        finalEmail
                );

                String response =
                        sendWorkerRequest(
                                requestBody.toString()
                        );

                JSONObject jsonResponse =
                        new JSONObject(response);

                boolean success =
                        jsonResponse.optBoolean(
                                "success",
                                false
                        );

                if (success) {

                    runOnMainThread(
                            () -> callback.onSuccess(null)
                    );

                } else {

                    String message =
                            jsonResponse.optString(
                                    "message",
                                    "Could not send the verification code."
                            );

                    runOnMainThread(
                            () -> callback.onError(message)
                    );
                }

            } catch (Exception e) {

                Log.e(
                        "AuthRepository",
                        "OTP request failed",
                        e
                );

                runOnMainThread(
                        () -> callback.onError(
                                "Could not connect to the verification service."
                        )
                );
            }
        });
    }

    // ---------------------------------------------------------
    // VERIFY REGISTRATION OTP
    // ---------------------------------------------------------

    public void verifyRegistrationCode(
            String email,
            String code,
            AuthCallback<Void> callback
    ) {

        email = email.trim().toLowerCase();
        code = code.trim();

        if (!EmailValidator.isValidVossieEmail(email)) {

            callback.onError(
                    "Invalid email. Please use your @vossie.net address."
            );

            return;
        }

        if (!code.matches("\\d{6}")) {

            callback.onError(
                    "Please enter the full 6-digit code."
            );

            return;
        }

        String finalEmail = email;
        String finalCode = code;

        executor.execute(() -> {

            try {

                JSONObject requestBody =
                        new JSONObject();

                requestBody.put(
                        "action",
                        "verify"
                );

                requestBody.put(
                        "email",
                        finalEmail
                );

                requestBody.put(
                        "code",
                        finalCode
                );

                String response =
                        sendWorkerRequest(
                                requestBody.toString()
                        );

                JSONObject jsonResponse =
                        new JSONObject(response);

                boolean success =
                        jsonResponse.optBoolean(
                                "success",
                                false
                        );

                if (!success) {

                    String message =
                            jsonResponse.optString(
                                    "message",
                                    "Verification failed."
                            );

                    runOnMainThread(
                            () -> callback.onError(message)
                    );

                    return;
                }

                // -------------------------------------------------
                // OTP IS CORRECT
                // -------------------------------------------------

                FirebaseUser user =
                        auth.getCurrentUser();

                if (user == null) {

                    runOnMainThread(
                            () -> callback.onError(
                                    "Could not find your account. Please log in again."
                            )
                    );

                    return;
                }

                String uid =
                        user.getUid();

                db.collection("users")
                        .document(uid)
                        .update(
                                "verified",
                                true
                        )
                        .addOnSuccessListener(unused ->
                                callback.onSuccess(null)
                        )
                        .addOnFailureListener(e ->
                                callback.onError(
                                        "Your email was verified, but we could not update your profile."
                                )
                        );

            } catch (Exception e) {

                Log.e(
                        "AuthRepository",
                        "OTP verification failed",
                        e
                );

                runOnMainThread(
                        () -> callback.onError(
                                "Could not connect to the verification service."
                        )
                );
            }
        });
    }

    // ---------------------------------------------------------
    // LOG IN
    // ---------------------------------------------------------

    public void logIn(
            String email,
            String password,
            AuthCallback<Void> callback
    ) {

        email = email.trim().toLowerCase();

        if (!EmailValidator.isValidVossieEmail(email)) {

            callback.onError(
                    "Invalid email. Please use your @vossie.net address."
            );

            return;
        }

        String finalEmail = email;

        auth.signInWithEmailAndPassword(
                        finalEmail,
                        password
                )
                .addOnSuccessListener(authResult -> {

                    FirebaseUser user =
                            authResult.getUser();

                    if (user == null) {

                        callback.onError(
                                "Could not log in. Please try again."
                        );

                        return;
                    }

                    db.collection("users")
                            .document(user.getUid())
                            .get()
                            .addOnSuccessListener(document -> {

                                if (!document.exists()) {

                                    auth.signOut();

                                    callback.onError(
                                            "Your account profile could not be found."
                                    );

                                    return;
                                }

                                Boolean verified =
                                        document.getBoolean(
                                                "verified"
                                        );

                                if (verified == null
                                        || !verified) {

                                    auth.signOut();

                                    callback.onError(
                                            "Please verify your email with the 6-digit code before logging in."
                                    );

                                    return;
                                }

                                callback.onSuccess(null);
                            })
                            .addOnFailureListener(e -> {

                                auth.signOut();

                                callback.onError(
                                        "Could not check your verification status."
                                );
                            });

                })
                .addOnFailureListener(e -> callback.onError(
                        e.getMessage() != null
                                ? e.getMessage()
                                : "Login failed. Please try again."
                ));
    }

    // ---------------------------------------------------------
    // FORGOT PASSWORD
    // ---------------------------------------------------------

    public void sendPasswordResetLink(
            String email,
            AuthCallback<Void> callback
    ) {

        email = email.trim().toLowerCase();

        if (!EmailValidator.isValidVossieEmail(email)) {

            callback.onError(
                    "Invalid email. Please use your @vossie.net address."
            );

            return;
        }

        ActionCodeSettings actionCodeSettings =
                ActionCodeSettings.newBuilder()
                        .setUrl(
                                "https://group526.web.app/resetPassword.html"
                        )
                        .setHandleCodeInApp(true)
                        .setAndroidPackageName(
                                "com.example.edusafe",
                                true,
                                null
                        )
                        .build();

        auth.sendPasswordResetEmail(
                        email,
                        actionCodeSettings
                )
                .addOnSuccessListener(unused ->
                        callback.onSuccess(null)
                )
                .addOnFailureListener(e ->
                        callback.onError(
                                e.getMessage() != null
                                        ? e.getMessage()
                                        : "Could not send the password reset email."
                        )
                );
    }

    // ---------------------------------------------------------
    // SEND REQUEST TO CLOUDFLARE
    // ---------------------------------------------------------

    private String sendWorkerRequest(
            String requestBody
    ) throws Exception {

        URL url =
                new URL(OTP_WORKER_URL);

        HttpURLConnection connection =
                (HttpURLConnection)
                        url.openConnection();

        connection.setRequestMethod(
                "POST"
        );

        connection.setRequestProperty(
                "Content-Type",
                "application/json"
        );

        connection.setRequestProperty(
                "Accept",
                "application/json"
        );

        connection.setConnectTimeout(
                15000
        );

        connection.setReadTimeout(
                15000
        );

        connection.setDoOutput(true);

        byte[] body =
                requestBody.getBytes(
                        StandardCharsets.UTF_8
                );

        try (OutputStream outputStream =
                     connection.getOutputStream()) {

            outputStream.write(body);
        }

        int responseCode =
                connection.getResponseCode();

        BufferedReader reader;

        if (responseCode >= 200
                && responseCode < 300) {

            reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    connection.getInputStream(),
                                    StandardCharsets.UTF_8
                            )
                    );

        } else {

            reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    connection.getErrorStream(),
                                    StandardCharsets.UTF_8
                            )
                    );
        }

        StringBuilder response =
                new StringBuilder();

        String line;

        while ((line = reader.readLine()) != null) {
            response.append(line);
        }

        reader.close();

        connection.disconnect();

        return response.toString();
    }

    // ---------------------------------------------------------
    // MAIN THREAD
    // ---------------------------------------------------------

    private void runOnMainThread(
            Runnable action
    ) {

        new Handler(
                Looper.getMainLooper()
        ).post(action);
    }
}