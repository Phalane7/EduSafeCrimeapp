package com.example.edusafe;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SignUpActivity extends AppCompatActivity {

    private EditText etFirstName, etSurname, etPhoneNumber, etEmail, etVerifyEmail,
            etPassword, etVerifyPassword;
    private TextView tvEmailError;
    private Button btnCreateAccount;

    private final AuthRepository authRepository = new AuthRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        etFirstName = findViewById(R.id.etFirstName);
        etSurname = findViewById(R.id.etSurname);
        etPhoneNumber = findViewById(R.id.etPhoneNumber);
        etEmail = findViewById(R.id.etEmail);
        etVerifyEmail = findViewById(R.id.etVerifyEmail);
        etPassword = findViewById(R.id.etPassword);
        etVerifyPassword = findViewById(R.id.etVerifyPassword);
        tvEmailError = findViewById(R.id.tvEmailError);

        TextView btnLogin = findViewById(R.id.btnLogin);
        TextView btnSignup = findViewById(R.id.btnSignup);
        btnCreateAccount = findViewById(R.id.btnCreateAccount);

        btnLogin.setOnClickListener(v -> {
            startActivity(new Intent(SignUpActivity.this, LoginActivity.class));
            finish();
        });
        btnSignup.setOnClickListener(v -> { }); // already on this screen

        btnCreateAccount.setOnClickListener(v -> attemptCreateAccount());
    }

    private void attemptCreateAccount() {
        String firstName = etFirstName.getText().toString().trim();
        String surname = etSurname.getText().toString().trim();
        String phone = etPhoneNumber.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String verifyEmail = etVerifyEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String verifyPassword = etVerifyPassword.getText().toString().trim();

        if (firstName.isEmpty() || surname.isEmpty() || phone.isEmpty() || email.isEmpty()
                || verifyEmail.isEmpty() || password.isEmpty() || verifyPassword.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!EmailValidator.isValidVossieEmail(email)) {
            tvEmailError.setVisibility(View.VISIBLE);
            etEmail.setBackgroundResource(R.drawable.bg_edittext_error);
            return;
        }
        tvEmailError.setVisibility(View.GONE);
        etEmail.setBackgroundResource(R.drawable.bg_edittext);

        if (!email.equals(verifyEmail)) {
            Toast.makeText(this, "Email addresses do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(verifyPassword)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 8) {
            Toast.makeText(this, "Password must be at least 8 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        btnCreateAccount.setEnabled(false);
        authRepository.signUp(firstName, surname, phone, email, password, new AuthCallback<>() {
            @Override
            public void onSuccess(Void result) {
                btnCreateAccount.setEnabled(true);
                Toast.makeText(SignUpActivity.this, "Account created. You can now log in.", Toast.LENGTH_LONG).show();
                startActivity(new Intent(SignUpActivity.this, LoginActivity.class));
                finish();
            }

            @Override
            public void onError(String message) {
                btnCreateAccount.setEnabled(true);
                Toast.makeText(SignUpActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }
}