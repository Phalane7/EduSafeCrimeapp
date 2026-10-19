package com.example.edusafe;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private TextView tvEmailError;
    private Button btnLogin;

    private AuthRepository authRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        authRepository = new AuthRepository(this);

        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tvEmailError = findViewById(R.id.tvEmailError);
        btnLogin = findViewById(R.id.btnLogin);

        TextView tvForgotPassword =
                findViewById(R.id.tvForgotPassword);

        TextView tvGoToSignup =
                findViewById(R.id.tvGoToSignup);

        btnLogin.setOnClickListener(v -> attemptLogin());

        tvForgotPassword.setOnClickListener(v ->
                startActivity(new Intent(
                        LoginActivity.this,
                        ForgotPasswordActivity.class
                ))
        );

        tvGoToSignup.setOnClickListener(v ->
                startActivity(new Intent(
                        LoginActivity.this,
                        SignUpActivity.class
                ))
        );
    }

    private void attemptLogin() {

        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();

        if (!EmailValidator.isValidVossieEmail(email)) {
            tvEmailError.setVisibility(View.VISIBLE);
            etEmail.setBackgroundResource(
                    R.drawable.bg_edittext_error);
            return;
        }

        tvEmailError.setVisibility(View.GONE);
        etEmail.setBackgroundResource(R.drawable.bg_edittext);

        if (password.isEmpty()) {
            Toast.makeText(this,
                    "Please enter your password",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        btnLogin.setEnabled(false);

        authRepository.logIn(
                email,
                password,
                new AuthCallback<Void>() {

                    @Override
                    public void onSuccess(Void result) {

                        btnLogin.setEnabled(true);

                        Toast.makeText(
                                LoginActivity.this,
                                "Login successful!",
                                Toast.LENGTH_SHORT
                        ).show();

                        Intent intent = new Intent(
                                LoginActivity.this,
                                MainActivity.class
                        );

                        intent.addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
                        );

                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onError(String message) {

                        btnLogin.setEnabled(true);

                        Toast.makeText(
                                LoginActivity.this,
                                message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }
}
