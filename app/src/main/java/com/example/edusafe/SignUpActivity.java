package com.example.edusafe;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SignUpActivity extends AppCompatActivity {

    private EditText etFirstName,
            etSurname,
            etPhoneNumber,
            etEmail,
            etVerifyEmail,
            etPassword,
            etVerifyPassword;

    private TextView tvEmailError;
    private TextView tvAccountSuccess;

    private Button btnCreateAccount;

    private final AuthRepository authRepository =
            new AuthRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_signup);

        // -------------------------------------------------
        // FIND VIEWS
        // -------------------------------------------------

        etFirstName =
                findViewById(R.id.etFirstName);

        etSurname =
                findViewById(R.id.etSurname);

        etPhoneNumber =
                findViewById(R.id.etPhoneNumber);

        etEmail =
                findViewById(R.id.etEmail);

        etVerifyEmail =
                findViewById(R.id.etVerifyEmail);

        etPassword =
                findViewById(R.id.etPassword);

        etVerifyPassword =
                findViewById(R.id.etVerifyPassword);

        tvEmailError =
                findViewById(R.id.tvEmailError);

        tvAccountSuccess =
                findViewById(R.id.tvAccountSuccess);

        TextView btnLogin =
                findViewById(R.id.btnLogin);

        TextView btnSignup =
                findViewById(R.id.btnSignup);

        btnCreateAccount =
                findViewById(R.id.btnCreateAccount);

        // -------------------------------------------------
        // GO TO LOGIN
        // -------------------------------------------------

        btnLogin.setOnClickListener(v -> {

            startActivity(
                    new Intent(
                            SignUpActivity.this,
                            LoginActivity.class
                    )
            );

            finish();
        });

        // -------------------------------------------------
        // SIGN UP
        // -------------------------------------------------

        btnSignup.setOnClickListener(v -> {
            // Already on sign-up screen
        });

        // -------------------------------------------------
        // CREATE ACCOUNT
        // -------------------------------------------------

        btnCreateAccount.setOnClickListener(
                v -> attemptCreateAccount()
        );
    }

    private void attemptCreateAccount() {

        String firstName =
                etFirstName.getText()
                        .toString()
                        .trim();

        String surname =
                etSurname.getText()
                        .toString()
                        .trim();

        String phone =
                etPhoneNumber.getText()
                        .toString()
                        .trim();

        String email =
                etEmail.getText()
                        .toString()
                        .trim()
                        .toLowerCase();

        String verifyEmail =
                etVerifyEmail.getText()
                        .toString()
                        .trim()
                        .toLowerCase();

        String password =
                etPassword.getText()
                        .toString()
                        .trim();

        String verifyPassword =
                etVerifyPassword.getText()
                        .toString()
                        .trim();

        // -------------------------------------------------
        // EMPTY FIELDS
        // -------------------------------------------------

        if (firstName.isEmpty()
                || surname.isEmpty()
                || phone.isEmpty()
                || email.isEmpty()
                || verifyEmail.isEmpty()
                || password.isEmpty()
                || verifyPassword.isEmpty()) {

            Toast.makeText(
                    this,
                    "Please fill in all fields",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        // -------------------------------------------------
        // VOSSIE EMAIL
        // -------------------------------------------------

        if (!EmailValidator.isValidVossieEmail(email)) {

            tvEmailError.setVisibility(
                    View.VISIBLE
            );

            etEmail.setBackgroundResource(
                    R.drawable.bg_edittext_error
            );

            return;
        }

        tvEmailError.setVisibility(
                View.GONE
        );

        etEmail.setBackgroundResource(
                R.drawable.bg_edittext
        );

        // -------------------------------------------------
        // EMAIL MATCH
        // -------------------------------------------------

        if (!email.equals(verifyEmail)) {

            Toast.makeText(
                    this,
                    "Email addresses do not match",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        // -------------------------------------------------
        // PASSWORD MATCH
        // -------------------------------------------------

        if (!password.equals(verifyPassword)) {

            Toast.makeText(
                    this,
                    "Passwords do not match",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        // -------------------------------------------------
        // PASSWORD LENGTH
        // -------------------------------------------------

        if (password.length() < 8) {

            Toast.makeText(
                    this,
                    "Password must be at least 8 characters",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        // -------------------------------------------------
        // DISABLE BUTTON
        // -------------------------------------------------

        btnCreateAccount.setEnabled(false);

        // -------------------------------------------------
        // CREATE ACCOUNT
        // -------------------------------------------------

        authRepository.signUp(
                firstName,
                surname,
                phone,
                email,
                password,
                new AuthCallback<>() {

                    @Override
                    public void onSuccess(
                            Void result
                    ) {

                        // -------------------------------------------------
                        // ACCOUNT SUCCESSFULLY CREATED
                        // -------------------------------------------------

                        tvAccountSuccess.setVisibility(
                                View.VISIBLE
                        );

                        // -------------------------------------------------
                        // START OTP REQUEST
                        //
                        // IMPORTANT:
                        // WE DO NOT WAIT FOR THIS.
                        // -------------------------------------------------

                        authRepository.sendRegistrationCode(
                                email,
                                new AuthCallback<>() {

                                    @Override
                                    public void onSuccess(
                                            Void result
                                    ) {
                                        // OTP request succeeded.
                                        // Nothing else is needed.
                                    }

                                    @Override
                                    public void onError(
                                            String message
                                    ) {
                                        // OTP request failed.
                                        //
                                        // Do nothing here.
                                        // The user must still go
                                        // to the OTP screen.
                                    }
                                }
                        );

                        // -------------------------------------------------
                        // GO TO OTP SCREEN
                        //
                        // DO NOT PUT THIS INSIDE THE OTP CALLBACK.
                        // -------------------------------------------------

                        new Handler(
                                Looper.getMainLooper()
                        ).postDelayed(
                                () -> {

                                    Intent intent =
                                            new Intent(
                                                    SignUpActivity.this,
                                                    VerifyEmailActivity.class
                                            );

                                    intent.putExtra(
                                            VerifyEmailActivity.EXTRA_EMAIL,
                                            email
                                    );

                                    startActivity(
                                            intent
                                    );

                                    finish();

                                },
                                1000
                        );
                    }

                    @Override
                    public void onError(
                            String message
                    ) {

                        btnCreateAccount.setEnabled(true);

                        Toast.makeText(
                                SignUpActivity.this,
                                message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }
}






