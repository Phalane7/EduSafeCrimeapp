package com.example.edusafe;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class VerifyEmailActivity extends AppCompatActivity {

    // OTP verification modes
    public static final String EXTRA_MODE = "extra_mode";
    public static final String MODE_SIGNUP = "signup";
    public static final String MODE_RECOVERY = "recovery";

    // Countdown: 2 minutes and 45 seconds
    private static final long RESEND_WINDOW_MS =
            2 * 60 * 1000 + 45 * 1000;

    private EditText[] otpBoxes;
    private TextView tvResendTimer;
    private Button btnVerify;

    private String email;
    private String mode;

    private CountDownTimer countDownTimer;

    // Supabase authentication repository
    private AuthRepository authRepository;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize Supabase authentication
        authRepository = new AuthRepository(this);

        // Keep existing OTP XML design
        setContentView(R.layout.activity_verify_email);

        // Get email from previous screen
        email = getIntent().getStringExtra(
                ForgotPasswordActivity.EXTRA_EMAIL
        );

        // Identify registration or password recovery
        mode = getIntent().getStringExtra(EXTRA_MODE);

        if (mode == null) {
            mode = MODE_RECOVERY;
        }

        // Make sure email was provided
        if (email == null || email.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    "Email address is missing.",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        TextView tvSentTo = findViewById(R.id.tvSentTo);
        tvResendTimer = findViewById(R.id.tvResendTimer);
        TextView tvGoBack = findViewById(R.id.tvGoBack);
        btnVerify = findViewById(R.id.btnVerify);

        // Display student's email address
        tvSentTo.setText(
                "We have sent a 6-digit code to\n" + email
        );

        // Connect the six OTP input boxes
        otpBoxes = new EditText[]{
                findViewById(R.id.etOtp1),
                findViewById(R.id.etOtp2),
                findViewById(R.id.etOtp3),
                findViewById(R.id.etOtp4),
                findViewById(R.id.etOtp5),
                findViewById(R.id.etOtp6)
        };

        // Automatically move between OTP boxes
        setUpOtpAutoAdvance();

        // Verify button
        btnVerify.setOnClickListener(v -> attemptVerify());

        // Back button
        tvGoBack.setOnClickListener(v -> {

            Intent intent;

            if (MODE_SIGNUP.equals(mode)) {

                intent = new Intent(
                        VerifyEmailActivity.this,
                        SignUpActivity.class
                );

            } else {

                intent = new Intent(
                        VerifyEmailActivity.this,
                        ForgotPasswordActivity.class
                );
            }

            startActivity(intent);
            finish();
        });

        // Start resend countdown
        startResendCountdown();
    }

    // -----------------------------------------
    // AUTOMATIC OTP BOX NAVIGATION
    // -----------------------------------------

    private void setUpOtpAutoAdvance() {

        for (int i = 0; i < otpBoxes.length; i++) {

            final int index = i;

            otpBoxes[i].addTextChangedListener(
                    new TextWatcher() {

                        @Override
                        public void beforeTextChanged(
                                CharSequence s,
                                int start,
                                int count,
                                int after) {
                        }

                        @Override
                        public void onTextChanged(
                                CharSequence s,
                                int start,
                                int before,
                                int count) {
                        }

                        @Override
                        public void afterTextChanged(
                                Editable s) {

                            if (s.length() == 1
                                    && index < otpBoxes.length - 1) {

                                otpBoxes[index + 1].requestFocus();
                            }
                        }
                    }
            );

            // Move backward when deleting
            otpBoxes[i].setOnKeyListener(
                    (v, keyCode, event) -> {

                        if (keyCode == KeyEvent.KEYCODE_DEL
                                && event.getAction()
                                == KeyEvent.ACTION_DOWN
                                && otpBoxes[index]
                                .getText()
                                .toString()
                                .isEmpty()
                                && index > 0) {

                            otpBoxes[index - 1].requestFocus();
                            otpBoxes[index - 1].setText("");
                        }

                        return false;
                    }
            );
        }
    }

    // -----------------------------------------
    // RESEND OTP COUNTDOWN
    // -----------------------------------------

    private void startResendCountdown() {

        // Cancel previous countdown if active
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        tvResendTimer.setOnClickListener(null);
        tvResendTimer.setClickable(false);

        countDownTimer = new CountDownTimer(
                RESEND_WINDOW_MS,
                1000
        ) {

            @SuppressLint("DefaultLocale")
            @Override
            public void onTick(long millisUntilFinished) {

                int minutes =
                        (int) (millisUntilFinished / 1000) / 60;

                int seconds =
                        (int) (millisUntilFinished / 1000) % 60;

                tvResendTimer.setText(
                        String.format(
                                "Resend code in %d:%02d",
                                minutes,
                                seconds
                        )
                );
            }

            @SuppressLint("SetTextI18n")
            @Override
            public void onFinish() {

                tvResendTimer.setText("Resend code");
                tvResendTimer.setClickable(true);

                tvResendTimer.setOnClickListener(
                        v -> resendCode()
                );
            }
        }.start();
    }

    // -----------------------------------------
    // RESEND OTP
    // -----------------------------------------

    private void resendCode() {

        tvResendTimer.setClickable(false);

        AuthCallback<Void> callback =
                new AuthCallback<Void>() {

                    @Override
                    public void onSuccess(Void result) {

                        Toast.makeText(
                                VerifyEmailActivity.this,
                                "New verification code requested. Check your email.",
                                Toast.LENGTH_LONG
                        ).show();

                        startResendCountdown();
                    }

                    @Override
                    public void onError(String message) {

                        tvResendTimer.setClickable(true);

                        Toast.makeText(
                                VerifyEmailActivity.this,
                                message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                };

        // Choose the correct Supabase endpoint
        if (MODE_SIGNUP.equals(mode)) {

            authRepository.resendSignUpCode(
                    email,
                    callback
            );

        } else {

            authRepository.sendPasswordResetCode(
                    email,
                    callback
            );
        }
    }

    // -----------------------------------------
    // VERIFY SIX-DIGIT OTP
    // -----------------------------------------

    private void attemptVerify() {

        StringBuilder code = new StringBuilder();

        // Collect digits from all six boxes
        for (EditText box : otpBoxes) {

            code.append(
                    box.getText().toString().trim()
            );
        }

        if (code.length() != 6) {

            Toast.makeText(
                    this,
                    "Please enter the full 6-digit code.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        btnVerify.setEnabled(false);

        final String enteredCode = code.toString();

        AuthCallback<Void> callback =
                new AuthCallback<Void>() {

                    @Override
                    public void onSuccess(Void result) {

                        btnVerify.setEnabled(true);

                        if (MODE_SIGNUP.equals(mode)) {

                            // Registration OTP verified
                            Toast.makeText(
                                    VerifyEmailActivity.this,
                                    "Email verified successfully! Please log in.",
                                    Toast.LENGTH_LONG
                            ).show();

                            Intent intent = new Intent(
                                    VerifyEmailActivity.this,
                                    LoginActivity.class
                            );

                            intent.addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_CLEAR_TASK
                            );

                            startActivity(intent);
                            finish();

                        } else {

                            // Recovery OTP verified.
                            // Password updating will be
                            // implemented with a Supabase
                            // recovery session in the next step.

                            Toast.makeText(
                                    VerifyEmailActivity.this,
                                    "Code verified. Password reset setup is not complete yet.",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }

                    @Override
                    public void onError(String message) {

                        btnVerify.setEnabled(true);

                        Toast.makeText(
                                VerifyEmailActivity.this,
                                message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                };

        if (MODE_SIGNUP.equals(mode)) {

            // Verify student registration OTP
            authRepository.verifySignUpCode(
                    email,
                    enteredCode,
                    callback
            );

        } else {

            // Verify password recovery OTP
            authRepository.verifyPasswordResetCode(
                    email,
                    enteredCode,
                    callback
            );
        }
    }

    // -----------------------------------------
    // CLEAN UP COUNTDOWN
    // -----------------------------------------

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}

