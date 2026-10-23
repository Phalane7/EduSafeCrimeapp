package com.example.edusafe;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class CreateNewPasswordActivity extends AppCompatActivity {

    private EditText etNewPassword;
    private EditText etConfirmPassword;

    private TextView tvSubtitle;
    private TextView tvPasswordError;

    private Button btnUpdatePassword;

    private AuthRepository authRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize Supabase authentication
        authRepository = new AuthRepository(this);

        // Keep your existing XML design
        setContentView(R.layout.activity_create_new_password);

        // -----------------------------------------
        // CONNECT XML VIEWS
        // -----------------------------------------

        etNewPassword = findViewById(R.id.etNewPassword);

        etConfirmPassword = findViewById(R.id.etConfirmPassword);

        tvSubtitle = findViewById(R.id.tvSubtitle);

        tvPasswordError = findViewById(R.id.tvPasswordError);

        btnUpdatePassword = findViewById(R.id.btnUpdatePassword);

        TextView tvBackToLogin =
                findViewById(R.id.tvBackToLogin);

        // -----------------------------------------
        // BACK TO LOGIN
        // -----------------------------------------

        tvBackToLogin.setOnClickListener(
                v -> goToLogin()
        );

        // -----------------------------------------
        // CHECK RECOVERY SESSION
        // -----------------------------------------

        if (!authRepository.hasRecoverySession()) {

            tvSubtitle.setText(
                    "Your password recovery session is missing or has expired. Please request a new code."
            );

            btnUpdatePassword.setEnabled(false);

            return;
        }

        tvSubtitle.setText(
                "Enter your new password below."
        );

        // -----------------------------------------
        // UPDATE PASSWORD BUTTON
        // -----------------------------------------

        btnUpdatePassword.setOnClickListener(
                v -> attemptUpdatePassword()
        );
    }

    // -----------------------------------------
    // VALIDATE AND UPDATE PASSWORD
    // -----------------------------------------

    private void attemptUpdatePassword() {

        String newPassword =
                etNewPassword.getText().toString();

        String confirmPassword =
                etConfirmPassword.getText().toString();

        // Password must contain at least 8 characters
        if (newPassword.length() < 8) {

            Toast.makeText(
                    this,
                    "Password must be at least 8 characters.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        // Check passwords match
        if (!newPassword.equals(confirmPassword)) {

            tvPasswordError.setVisibility(View.VISIBLE);

            Toast.makeText(
                    this,
                    "Passwords do not match.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        tvPasswordError.setVisibility(View.GONE);

        btnUpdatePassword.setEnabled(false);

        // -----------------------------------------
        // UPDATE PASSWORD USING SUPABASE
        // -----------------------------------------

        authRepository.updateRecoveredPassword(
                newPassword,
                new AuthCallback<Void>() {

                    @Override
                    public void onSuccess(Void result) {

                        Toast.makeText(
                                CreateNewPasswordActivity.this,
                                "Password updated successfully! Please log in.",
                                Toast.LENGTH_LONG
                        ).show();

                        // Return to Login
                        goToLogin();
                    }

                    @Override
                    public void onError(String message) {

                        btnUpdatePassword.setEnabled(true);

                        Toast.makeText(
                                CreateNewPasswordActivity.this,
                                message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }

    // -----------------------------------------
    // RETURN TO LOGIN SCREEN
    // -----------------------------------------

    private void goToLogin() {

        authRepository.clearRecoverySession();

        Intent intent = new Intent(
                CreateNewPasswordActivity.this,
                LoginActivity.class
        );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }
}

