package com.example.edusafe;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

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
    private View tabSos;
    private View studentBottomNavigation;
    private View contentContainer;
    private AuthRepository authRepository;
    private String currentRole;
    private boolean checkingSessionOnResume = false;

    private final ActivityResultLauncher<String> askNotifications =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> { if (granted) PushRegistrar.init(this); });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        authRepository = new AuthRepository(this);

        tabHome = findViewById(R.id.tabHome);
        tabMaps = findViewById(R.id.tabMaps);
        tabReports = findViewById(R.id.tabReports);
        tabProfile = findViewById(R.id.tabProfile);
        tabSos = findViewById(R.id.tabSos);
        studentBottomNavigation = findViewById(R.id.studentBottomNavigation);
        contentContainer = findViewById(R.id.container);

        tabHome.setOnClickListener(v -> openHome());
        tabMaps.setOnClickListener(v -> {
            if ("student".equals(currentRole)) show(new CrimeMapFragment(), tabMaps);
        });
        tabReports.setOnClickListener(v -> {
            if ("student".equals(currentRole)) show(new ReportIncidentFragment(), tabReports);
        });
        tabProfile.setOnClickListener(v -> {
            if ("student".equals(currentRole)) show(new ProfileFragment(), tabProfile);
        });
        tabSos.setOnClickListener(v -> openEmergencySos());

        getSupportFragmentManager().addOnBackStackChangedListener(
                () -> SyncManager.syncAll(this, null));
        Notifier.ensureChannel(this);

        // Verify session and role before opening any dashboard.
        checkRoleAndOpen(savedInstanceState == null);
    }

    private void checkRoleAndOpen(boolean firstLaunch) {
        if (!authRepository.hasValidSession()) {
            returnToLogin("Please log in to continue.");
            return;
        }
        authRepository.getUserRole(new AuthCallback<String>() {
            @Override
            public void onSuccess(String role) {
                currentRole = role;
                boolean student = "student".equals(role);
                setStudentTabsEnabled(student);

                if ("admin".equals(role)) {
                    showStaff(new AdminFragment());
                } else if ("security".equals(role)) {
                    showStaff(new SecurityDashboardFragment());
                } else if (student && (firstLaunch ||
                        getSupportFragmentManager().findFragmentById(R.id.container) == null)) {
                    openRequestedScreen(getIntent());
                }

                if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(MainActivity.this,
                                Manifest.permission.POST_NOTIFICATIONS)
                                != PackageManager.PERMISSION_GRANTED) {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS);
                }
            }

            @Override
            public void onError(String message) {
                authRepository.clearSession();
                returnToLogin("Cannot verify account access: " + message);
            }
        });
    }

    private void setStudentTabsEnabled(boolean enabled) {
        tabHome.setVisibility(enabled ? View.VISIBLE : View.GONE);
        tabMaps.setVisibility(enabled ? View.VISIBLE : View.GONE);
        tabReports.setVisibility(enabled ? View.VISIBLE : View.GONE);
        tabProfile.setVisibility(enabled ? View.VISIBLE : View.GONE);
        tabSos.setVisibility(enabled ? View.VISIBLE : View.GONE);

        // Hide the entire floating navigation pill for admin/security accounts.
        // Hiding only the individual tabs leaves an empty white pill visible.
        studentBottomNavigation.setVisibility(enabled ? View.VISIBLE : View.GONE);

        // Staff screens should fill the whole available area; student screens
        // retain space for the floating navigation bar.
        ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) contentContainer.getLayoutParams();
        params.bottomMargin = enabled ? dp(85) : 0;
        contentContainer.setLayoutParams(params);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showStaff(Fragment fragment) {
        FragmentManager fm = getSupportFragmentManager();
        fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        fm.beginTransaction().replace(R.id.container, fragment).commit();
    }

    private void returnToLogin(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void openRequestedScreen(Intent intent) {
        if (!"student".equals(currentRole)) return;
        String tab = intent == null ? null : intent.getStringExtra(EXTRA_START_TAB);
        if (TAB_REPORTS.equals(tab)) show(new ReportIncidentFragment(), tabReports);
        else if (TAB_PROFILE.equals(tab)) show(new ProfileFragment(), tabProfile);
        else if (TAB_FEED.equals(tab) || TAB_HOME.equals(tab)) openFeed();
        else if (TAB_MAPS.equals(tab)) show(new CrimeMapFragment(), tabMaps);
        else openEmergencySos();
    }

    public void openHome() {
        // Bottom Home tab opens the News Safety Feed.
        openFeed();
    }

    public void openEmergencySos() {
        // The central blue SOS button and default app launch open Emergency SOS.
        if ("student".equals(currentRole)) show(new EmergencySosFragment(), null);
    }

    public void openFeed() {
        if ("student".equals(currentRole)) show(new FeedFragment(), tabHome);
    }

    private void show(Fragment fragment, TextView selected) {
        if (!"student".equals(currentRole)) return;
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

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openRequestedScreen(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // onResume also runs during the initial launch. The initial role check
        // is already handled by onCreate, so only recheck an established role.
        if (currentRole == null || checkingSessionOnResume) return;

        checkingSessionOnResume = true;
        final String previousRole = currentRole;
        // Disable role-gated navigation while verifying access.
        currentRole = null;
        if (!authRepository.hasValidSession()) {
            checkingSessionOnResume = false;
            authRepository.clearSession();
            returnToLogin("Your session has expired. Please log in again.");
            return;
        }
        authRepository.getUserRole(new AuthCallback<String>() {
            @Override
            public void onSuccess(String role) {
                checkingSessionOnResume = false;
                if (isFinishing() || isDestroyed()) return;
                if (!previousRole.equals(role)) {
                    authRepository.clearSession();
                    returnToLogin("Your account access has changed. Please log in again.");
                    return;
                }
                currentRole = role;
                if ("student".equals(role)) {
                    SyncManager.syncAll(MainActivity.this, null);
                    PushRegistrar.init(MainActivity.this);
                }
            }

            @Override
            public void onError(String message) {
                checkingSessionOnResume = false;
                if (isFinishing() || isDestroyed()) return;
                authRepository.clearSession();
                returnToLogin("Cannot verify account access: " + message);
            }
        });
    }
}


