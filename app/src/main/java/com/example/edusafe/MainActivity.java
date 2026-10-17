package com.example.edusafe;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.example.edusafe.ui.sos.EmergencySosFragment;
import com.example.edusafe.ui.map.CrimeMapFragment;

public class MainActivity extends AppCompatActivity {
    public static final String EXTRA_START_TAB = "start_tab";
    public static final String TAB_REPORTS = "reports";
    public static final String TAB_PROFILE = "profile";
    public static final String TAB_FEED = "feed";
    public static final String TAB_HOME = "home";
    public static final String TAB_MAPS = "maps";

    private TextView tabHome, tabMaps, tabReports, tabProfile;
    private final ActivityResultLauncher<String> askNotifications =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> { if (granted) PushRegistrar.init(this); });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tabHome = findViewById(R.id.tabHome);
        tabMaps = findViewById(R.id.tabMaps);
        tabReports = findViewById(R.id.tabReports);
        tabProfile = findViewById(R.id.tabProfile);
        tabHome.setOnClickListener(v -> openHome());
        tabMaps.setOnClickListener(v -> show(new CrimeMapFragment(), tabMaps));
        tabReports.setOnClickListener(v -> show(new ReportIncidentFragment(), tabReports));
        tabProfile.setOnClickListener(v -> show(new ProfileFragment(), tabProfile));
        findViewById(R.id.tabSos).setOnClickListener(v -> openHome());
        getSupportFragmentManager().addOnBackStackChangedListener(() -> SyncManager.syncAll(this, null));
        Notifier.ensureChannel(this);
        if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (savedInstanceState == null) openRequestedScreen(getIntent());
    }

    private void openRequestedScreen(Intent intent) {
        String tab = intent == null ? null : intent.getStringExtra(EXTRA_START_TAB);
        if (TAB_REPORTS.equals(tab)) show(new ReportIncidentFragment(), tabReports);
        else if (TAB_PROFILE.equals(tab)) show(new ProfileFragment(), tabProfile);
        else if (TAB_FEED.equals(tab)) openFeed();
        else if (TAB_MAPS.equals(tab)) show(new CrimeMapFragment(), tabMaps);
        else openHome();
    }

    public void openHome() { show(new EmergencySosFragment(), tabHome); }
    public void openFeed() { show(new FeedFragment(), tabHome); }

    private void show(Fragment fragment, TextView selected) {
        SyncManager.syncAll(this, null);
        FragmentManager fm = getSupportFragmentManager();
        fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        fm.beginTransaction().replace(R.id.container, fragment).commit();
        TextView[] tabs = {tabHome, tabMaps, tabReports, tabProfile};
        for (TextView tab : tabs) {
            int color = getColor(tab == selected ? R.color.navy : R.color.muted);
            tab.setTextColor(color);
            tab.setCompoundDrawableTintList(ColorStateList.valueOf(color));
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openRequestedScreen(intent);
    }
    @Override protected void onResume() {
        super.onResume();
        SyncManager.syncAll(this, null);
        PushRegistrar.init(this);
    }
}


