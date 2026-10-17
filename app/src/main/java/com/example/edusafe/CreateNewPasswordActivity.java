package com.example.edusafe;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class CreateNewPasswordActivity extends AppCompatActivity {

    private EditText etNewPassword;
    private EditText etConfirmPassword;

    private TextView tvSubtitle;
    private TextView tvPasswordError;

    private Button btnUpdatePassword;

    private String oobCode;

    private final FirebaseAuth auth =
            FirebaseAuth.getInstance();

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_create_new_password
        );

        // -------------------------------------------------
        // Find views
        // -------------------------------------------------

        etNewPassword =
                findViewById(R.id.etNewPassword);

        etConfirmPassword =
                findViewById(R.id.etConfirmPassword);

        tvSubtitle =
                findViewById(R.id.tvSubtitle);

        tvPasswordError =
                findViewById(R.id.tvPasswordError);

        TextView tvBackToLogin = findViewById(R.id.tvBackToLogin);

        btnUpdatePassword =
                findViewById(R.id.btnUpdatePassword);

        // -------------------------------------------------
        // Back to login
        // -------------------------------------------------

        tvBackToLogin.setOnClickListener(
                v -> goToLogin()
        );

        // -------------------------------------------------
        // Get Firebase reset code from link
        // -------------------------------------------------

        oobCode =
                extractOobCode(getIntent());

        if (oobCode == null
                || oobCode.isEmpty()) {

            tvSubtitle.setText(
                    "This reset link is invalid or has expired. Please request a new one."
            );

            btnUpdatePassword.setEnabled(false);

            return;
        }

        // -------------------------------------------------
        // Check whether reset link is valid
        // -------------------------------------------------

        verifyCodeIsStillValid();

        btnUpdatePassword.setOnClickListener(
                v -> attemptUpdatePassword()
        );
    }

    // ---------------------------------------------------------
    // GET OOB CODE
    // ---------------------------------------------------------

    private String extractOobCode(Intent intent) {

        if (intent == null) {
            return null;
        }

        Uri data =
                intent.getData();

        if (data == null) {
            return null;
        }

        return data.getQueryParameter(
                "oobCode"
        );
    }

    // ---------------------------------------------------------
    // CHECK RESET LINK
    // ---------------------------------------------------------

    private void verifyCodeIsStillValid() {

        btnUpdatePassword.setEnabled(false);

        auth.verifyPasswordResetCode(
                        oobCode
                )
                .addOnSuccessListener(email -> {

                    tvSubtitle.setText(
                            "Enter a new password for " + email + "."
                    );

                    btnUpdatePassword.setEnabled(
                            true
                    );
                })
                .addOnFailureListener(e -> {

                    tvSubtitle.setText(
                            "This reset link is invalid or has expired. Please request a new one."
                    );

                    btnUpdatePassword.setEnabled(
                            false
                    );
                });
    }

    // ---------------------------------------------------------
    // UPDATE PASSWORD
    // ---------------------------------------------------------

    private void attemptUpdatePassword() {

        String newPassword =
                etNewPassword.getText()
                        .toString()
                        .trim();

        String confirmPassword =
                etConfirmPassword.getText()
                        .toString()
                        .trim();

        // -------------------------------------------------
        // Password length
        // -------------------------------------------------

        if (newPassword.length() < 8) {

            Toast.makeText(
                    this,
                    "Password must be at least 8 characters",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        // -------------------------------------------------
        // Password confirmation
        // -------------------------------------------------

        if (!newPassword.equals(
                confirmPassword
        )) {

            tvPasswordError.setVisibility(
                    View.VISIBLE
            );

            return;
        }

        tvPasswordError.setVisibility(
                View.GONE
        );

        btnUpdatePassword.setEnabled(
                false
        );

        // -------------------------------------------------
        // Firebase changes password
        // -------------------------------------------------

        auth.confirmPasswordReset(
                        oobCode,
                        newPassword
                )
                .addOnSuccessListener(
                        unused -> {

                            Toast.makeText(
                                    this,
                                    "Password updated. Please log in.",
                                    Toast.LENGTH_LONG
                            ).show();

                            goToLogin();
                        }
                )
                .addOnFailureListener(
                        e -> {

                            btnUpdatePassword.setEnabled(
                                    true
                            );

                            Toast.makeText(
                                    this,
                                    e.getMessage() != null
                                            ? e.getMessage()
                                            : "Could not update your password.",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                );
    }

    // ---------------------------------------------------------
    // GO TO LOGIN
    // ---------------------------------------------------------

    private void goToLogin() {

        Intent intent =
                new Intent(
                        CreateNewPasswordActivity.this,
                        LoginActivity.class
                );

        intent.setFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NEW_TASK
        );

        startActivity(intent);

        finish();
    }
}
