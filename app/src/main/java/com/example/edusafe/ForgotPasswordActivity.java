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

    private AuthRepository authRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        authRepository = new AuthRepository(this);

        setContentView(R.layout.activity_forgot_password);

        etEmail = findViewById(R.id.etEmail);
        tvEmailError = findViewById(R.id.tvEmailError);
        btnNext = findViewById(R.id.btnNext);

        TextView tvBackToLogin =
                findViewById(R.id.tvBackToLogin);

        btnNext.setOnClickListener(v -> attemptSendCode());

        tvBackToLogin.setOnClickListener(v -> {
            startActivity(new Intent(
                    ForgotPasswordActivity.this,
                    LoginActivity.class
            ));
            finish();
        });
    }

    private void attemptSendCode() {

        String email = etEmail.getText()
                .toString().trim();

        if (!EmailValidator.isValidVossieEmail(email)) {

            tvEmailError.setVisibility(View.VISIBLE);

            etEmail.setBackgroundResource(
                    R.drawable.bg_edittext_error
            );

            return;
        }

        tvEmailError.setVisibility(View.GONE);

        etEmail.setBackgroundResource(
                R.drawable.bg_edittext
        );

        btnNext.setEnabled(false);

        authRepository.sendPasswordResetCode(
                email,
                new AuthCallback<Void>() {

                    @Override
                    public void onSuccess(Void result) {

                        btnNext.setEnabled(true);

                        Toast.makeText(
                                ForgotPasswordActivity.this,
                                "If the account exists, check your email for a recovery code.",
                                Toast.LENGTH_LONG
                        ).show();

                        Intent intent = new Intent(
                                ForgotPasswordActivity.this,
                                VerifyEmailActivity.class
                        );

                        intent.putExtra(EXTRA_EMAIL, email);

                        intent.putExtra(
                                VerifyEmailActivity.EXTRA_MODE,
                                VerifyEmailActivity.MODE_RECOVERY
                        );

                        startActivity(intent);
                    }

                    @Override
                    public void onError(String message) {

                        btnNext.setEnabled(true);

                        Toast.makeText(
                                ForgotPasswordActivity.this,
                                message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }
}
