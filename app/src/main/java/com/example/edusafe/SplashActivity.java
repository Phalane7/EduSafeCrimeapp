package com.example.edusafe;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 2000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable goNext = this::routeUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        handler.postDelayed(goNext, SPLASH_DELAY_MS);
    }

    private void routeUser() {
        // Already signed in -> straight to Home. Otherwise -> Get Started.
        boolean signedIn = FirebaseAuth.getInstance().getCurrentUser() != null;
        Class<?> destination = signedIn ? HomeActivity.class : GetStartedActivity.class;

        startActivity(new Intent(SplashActivity.this, destination));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish(); // so Back never returns to the splash
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(goNext);
    }
}


