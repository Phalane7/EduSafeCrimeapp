package com.example.edusafe;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;

/**
 * Wraps every Firebase call the app needs, so the Activities stay UI-only.
 *
 * Sign up / log in use Firebase Authentication directly (email + password).
 * The "6-digit code" forgot-password flow is NOT something Firebase Auth
 * supports out of the box (its built-in reset only sends a link), so those
 * three calls go through Cloud Functions defined in /functions/index.js:
 *   - sendPasswordResetCode
 *   - verifyPasswordResetCode
 *   - resetPasswordWithCode
 */
public class AuthRepository {

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseFunctions functions = FirebaseFunctions.getInstance();

    // ---------- Sign up ----------

    public void signUp(String firstName, String surname, String phone, String email,
                       String password, AuthCallback<Void> callback) {

        if (!EmailValidator.isValidVossieEmail(email)) {
            callback.onError("Invalid email. Please use your @vossie.net address.");
            return;
        }

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user == null) {
                        callback.onError("Account was not created. Please try again.");
                        return;
                    }

                    // Store profile fields Firebase Auth itself doesn't hold.
                    Map<String, Object> profile = new HashMap<>();
                    profile.put("firstName", firstName);
                    profile.put("surname", surname);
                    profile.put("phone", phone);
                    profile.put("email", email);
                    profile.put("verified", false); // flips to true once an admin approves the account
                    profile.put("banned", false);

                    db.collection("users").document(user.getUid())
                            .set(profile)
                            .addOnSuccessListener(unused -> callback.onSuccess(null))
                            .addOnFailureListener(e -> callback.onError("Account created, but saving your profile failed: " + e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ---------- Log in ----------

    public void logIn(String email, String password, AuthCallback<Void> callback) {

        if (!EmailValidator.isValidVossieEmail(email)) {
            callback.onError("Invalid email. Please use your @vossie.net address.");
            return;
        }

        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ---------- Forgot password: step 1, send the 6-digit code ----------

    public void sendPasswordResetCode(String email, AuthCallback<Void> callback) {

        if (!EmailValidator.isValidVossieEmail(email)) {
            callback.onError("Invalid email. Please use your @vossie.net address.");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("email", email);

        functions.getHttpsCallable("sendPasswordResetCode")
                .call(data)
                .addOnSuccessListener(result -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(describe(e)));
    }

    // ---------- Forgot password: step 2, verify the code ----------

    public void verifyPasswordResetCode(String email, String code, AuthCallback<Void> callback) {

        Map<String, Object> data = new HashMap<>();
        data.put("email", email);
        data.put("code", code);

        functions.getHttpsCallable("verifyPasswordResetCode")
                .call(data)
                .addOnSuccessListener(result -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(describe(e)));
    }

    // ---------- Forgot password: step 3, set the new password ----------

    public void resetPasswordWithCode(String email, String code, String newPassword, AuthCallback<Void> callback) {

        Map<String, Object> data = new HashMap<>();
        data.put("email", email);
        data.put("code", code);
        data.put("newPassword", newPassword);

        functions.getHttpsCallable("resetPasswordWithCode")
                .call(data)
                .addOnSuccessListener(result -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(describe(e)));
    }

    private String describe(@NonNull Exception e) {
        if (e instanceof FirebaseFunctionsException) {
            return ((FirebaseFunctionsException) e).getMessage();
        }
        return e.getMessage() != null ? e.getMessage() : "Something went wrong. Please try again.";
    }
}

