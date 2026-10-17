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

    public static final String EXTRA_EMAIL = "extra_email";

    private static final long RESEND_WINDOW_MS =
            2 * 60 * 1000 + 45 * 1000;

    private EditText[] otpBoxes;

    private TextView tvResendTimer;

    private Button btnVerify;

    private String email;

    private CountDownTimer countDownTimer;

    private final AuthRepository authRepository =
            new AuthRepository();

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_verify_email);

        // -------------------------------------------------
        // Get email from SignUpActivity
        // -------------------------------------------------

        email = getIntent().getStringExtra(EXTRA_EMAIL);

        if (email == null || email.trim().isEmpty()) {

            Toast.makeText(
                    this,
                    "Email address is missing.",
                    Toast.LENGTH_LONG
            ).show();

            finish();

            return;
        }

        email = email.trim().toLowerCase();

        // -------------------------------------------------
        // Find views
        // -------------------------------------------------

        TextView tvSentTo =
                findViewById(R.id.tvSentTo);

        tvResendTimer =
                findViewById(R.id.tvResendTimer);

        TextView tvGoBack =
                findViewById(R.id.tvGoBack);

        btnVerify =
                findViewById(R.id.btnVerify);

        tvSentTo.setText(
                "We have sent a 6-digit code to\n" + email
        );

        // -------------------------------------------------
        // OTP boxes
        // -------------------------------------------------

        otpBoxes = new EditText[]{
                findViewById(R.id.etOtp1),
                findViewById(R.id.etOtp2),
                findViewById(R.id.etOtp3),
                findViewById(R.id.etOtp4),
                findViewById(R.id.etOtp5),
                findViewById(R.id.etOtp6)
        };

        setUpOtpAutoAdvance();

        // -------------------------------------------------
        // Verify button
        // -------------------------------------------------

        btnVerify.setOnClickListener(
                v -> attemptVerify()
        );

        // -------------------------------------------------
        // Go back
        // -------------------------------------------------

        tvGoBack.setOnClickListener(v -> {

            startActivity(
                    new Intent(
                            VerifyEmailActivity.this,
                            SignUpActivity.class
                    )
            );

            finish();
        });

        // -------------------------------------------------
        // Start resend timer
        // -------------------------------------------------

        startResendCountdown();
    }

    // ---------------------------------------------------------
    // OTP BOXES
    // ---------------------------------------------------------

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
                                int after
                        ) {
                        }

                        @Override
                        public void onTextChanged(
                                CharSequence s,
                                int start,
                                int before,
                                int count
                        ) {
                        }

                        @Override
                        public void afterTextChanged(
                                Editable s
                        ) {

                            if (s.length() == 1
                                    && index < otpBoxes.length - 1) {

                                otpBoxes[index + 1]
                                        .requestFocus();
                            }
                        }
                    }
            );

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

                            otpBoxes[index - 1]
                                    .requestFocus();

                            otpBoxes[index - 1]
                                    .setText("");
                        }

                        return false;
                    }
            );
        }
    }

    // ---------------------------------------------------------
    // RESEND TIMER
    // ---------------------------------------------------------

    private void startResendCountdown() {

        tvResendTimer.setClickable(false);

        countDownTimer =
                new CountDownTimer(
                        RESEND_WINDOW_MS,
                        1000
                ) {

                    @SuppressLint("DefaultLocale")
                    @Override
                    public void onTick(
                            long millisUntilFinished
                    ) {

                        int minutes =
                                (int)
                                        (millisUntilFinished / 1000)
                                        / 60;

                        int seconds =
                                (int)
                                        (millisUntilFinished / 1000)
                                        % 60;

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

                        tvResendTimer.setText(
                                "Resend code"
                        );

                        tvResendTimer.setClickable(true);

                        tvResendTimer.setOnClickListener(
                                v -> resendCode()
                        );
                    }
                }.start();
    }

    // ---------------------------------------------------------
    // RESEND REGISTRATION OTP
    // ---------------------------------------------------------

    private void resendCode() {

        tvResendTimer.setClickable(false);

        authRepository.sendRegistrationCode(
                email,
                new AuthCallback<>() {

                    @Override
                    public void onSuccess(Void result) {

                        Toast.makeText(
                                VerifyEmailActivity.this,
                                "New verification code sent.",
                                Toast.LENGTH_SHORT
                        ).show();

                        clearOtpBoxes();

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
                }
        );
    }

    // ---------------------------------------------------------
    // VERIFY REGISTRATION OTP
    // ---------------------------------------------------------

    private void attemptVerify() {

        StringBuilder code =
                new StringBuilder();

        for (EditText box : otpBoxes) {

            code.append(
                    box.getText()
                            .toString()
                            .trim()
            );
        }

        if (code.length() != 6) {

            Toast.makeText(
                    this,
                    "Please enter the full 6-digit code",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        btnVerify.setEnabled(false);

        authRepository.verifyRegistrationCode(
                email,
                code.toString(),
                new AuthCallback<>() {

                    @Override
                    public void onSuccess(Void result) {

                        btnVerify.setEnabled(true);

                        Toast.makeText(
                                VerifyEmailActivity.this,
                                "Email verified successfully!",
                                Toast.LENGTH_LONG
                        ).show();

                        /*
                         * Registration is complete.
                         *
                         * DO NOT open CreateNewPasswordActivity.
                         *
                         * The user should now log in.
                         */

                        Intent intent =
                                new Intent(
                                        VerifyEmailActivity.this,
                                        LoginActivity.class
                                );

                        startActivity(intent);

                        finish();
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
                }
        );
    }

    // ---------------------------------------------------------
    // CLEAR OTP BOXES
    // ---------------------------------------------------------

    private void clearOtpBoxes() {

        for (EditText box : otpBoxes) {
            box.setText("");
        }

        otpBoxes[0].requestFocus();
    }

    // ---------------------------------------------------------
    // CLEAN UP TIMER
    // ---------------------------------------------------------

    @Override
    protected void onDestroy() {

        super.onDestroy();

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}