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

    public static final String EXTRA_CODE = "extra_code";

    private EditText etNewPassword, etConfirmPassword;
    private TextView tvPasswordError;
    private Button btnUpdatePassword;

    private String email;
    private String code;

    private final AuthRepository authRepository = new AuthRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_new_password);

        email = getIntent().getStringExtra(ForgotPasswordActivity.EXTRA_EMAIL);
        code = getIntent().getStringExtra(EXTRA_CODE);

        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        tvPasswordError = findViewById(R.id.tvPasswordError);
        TextView tvBackToLogin = findViewById(R.id.tvBackToLogin);
        btnUpdatePassword = findViewById(R.id.btnUpdatePassword);

        btnUpdatePassword.setOnClickListener(v -> attemptUpdatePassword());

        tvBackToLogin.setOnClickListener(v -> {
            startActivity(new Intent(CreateNewPasswordActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void attemptUpdatePassword() {
        String newPassword = etNewPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        if (newPassword.length() < 8) {
            Toast.makeText(this, "Password must be at least 8 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            tvPasswordError.setVisibility(View.VISIBLE);
            return;
        }
        tvPasswordError.setVisibility(View.GONE);

        btnUpdatePassword.setEnabled(false);
        authRepository.resetPasswordWithCode(email, code, newPassword, new AuthCallback<>() {
            @Override
            public void onSuccess(Void result) {
                btnUpdatePassword.setEnabled(true);
                Toast.makeText(CreateNewPasswordActivity.this, "Password updated. Please log in.", Toast.LENGTH_LONG).show();
                startActivity(new Intent(CreateNewPasswordActivity.this, LoginActivity.class));
                finish();
            }

            @Override
            public void onError(String message) {
                btnUpdatePassword.setEnabled(true);
                Toast.makeText(CreateNewPasswordActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }
}

