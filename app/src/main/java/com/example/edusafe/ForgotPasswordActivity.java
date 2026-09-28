package com.example.edusafe;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ForgotPasswordActivity extends AppCompatActivity {

    public static final String EXTRA_EMAIL = "extra_email";

    private EditText etEmail;
    private TextView tvEmailError;
    private Button btnNext;

    private final AuthRepository authRepository = new AuthRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        etEmail = findViewById(R.id.etEmail);
        tvEmailError = findViewById(R.id.tvEmailError);
        TextView tvBackToLogin = findViewById(R.id.tvBackToLogin);
        btnNext = findViewById(R.id.btnNext);

        btnNext.setOnClickListener(v -> attemptSendCode());

        tvBackToLogin.setOnClickListener(v -> {
            startActivity(new Intent(ForgotPasswordActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void attemptSendCode() {
        String email = etEmail.getText().toString().trim();

        if (!EmailValidator.isValidVossieEmail(email)) {
            tvEmailError.setVisibility(View.VISIBLE);
            etEmail.setBackgroundResource(R.drawable.bg_edittext_error);
            return;
        }
        tvEmailError.setVisibility(View.GONE);
        etEmail.setBackgroundResource(R.drawable.bg_edittext);

        btnNext.setEnabled(false);
        authRepository.sendPasswordResetCode(email, new AuthCallback<>() {
            @Override
            public void onSuccess(Void result) {
                btnNext.setEnabled(true);
                Intent intent = new Intent(ForgotPasswordActivity.this, VerifyEmailActivity.class);
                intent.putExtra(EXTRA_EMAIL, email);
                startActivity(intent);
            }

            @Override
            public void onError(String message) {
                btnNext.setEnabled(true);
                Toast.makeText(ForgotPasswordActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }
}
