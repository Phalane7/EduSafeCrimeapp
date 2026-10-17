package com.example.edusafe.ui.common;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.edusafe.R;

/**
 * PlaceholderActivity
 * ----------------------------------------------------------------------
 * A real, working navigation target for the bottom nav's Home / Maps /
 * Reports / Profile tabs. It's intentionally minimal — the point is
 * that tapping those tabs actually launches a new screen rather than
 * doing nothing — swap each call site over to your real Activity
 * (HomeActivity, CrimeMapActivity, etc.) as you build them.
 * ----------------------------------------------------------------------
 */
public class PlaceholderActivity extends AppCompatActivity {

    private static final String EXTRA_TITLE = "title";

    public static Intent newIntent(Context context, String title) {
        Intent intent = new Intent(context, PlaceholderActivity.class);
        intent.putExtra(EXTRA_TITLE, title);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_placeholder);

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        TextView titleView = findViewById(R.id.placeholderTitle);
        titleView.setText(title != null ? title : "Screen");

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
    }
}
