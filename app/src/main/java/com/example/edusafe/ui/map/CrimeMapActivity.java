package com.example.edusafe.ui.map;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.text.SimpleDateFormat;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import com.example.edusafe.SupabaseConfig;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.time.Instant;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.edusafe.R;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CrimeMapActivity  (screen 3 of 3)
 * ----------------------------------------------------------------------
 * Lets a student see reported safety incidents around Eduvos Midrand.
 *
 *   - filter chips (All / Robbery / Theft / Assault / Other) show or hide pins
 *   - the search box filters by incident or place name as you type
 *   - the sliders button limits how recent the incidents are
 *   - the layers button switches between satellite and street map
 *   - tapping a pin selects it: the map slides to it and the card updates
 *
 * The incidents are sample data (see IncidentRepository).
 * ----------------------------------------------------------------------
 */
public class CrimeMapActivity extends AppCompatActivity {

    private final List<Incident> allIncidents = new ArrayList<>();
    private final OkHttpClient client = new OkHttpClient();
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private boolean loading = false;
    private boolean mapActive = false;
    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            if (!mapActive) return;
            loadVerifiedIncidents();
            refreshHandler.postDelayed(this, 5000L);
        }
    };
    private List<Incident> shown = new ArrayList<>();

    private IncidentType selectedType = null;          // null means "All"
    private int maxHoursAgo = Integer.MAX_VALUE;       // any time
    private Incident selected;
    private boolean firstLoad = true;
    private boolean hasLoadedIncidents = false;
    private boolean warnedAboutSession = false;
    private static final double CAMPUS_LAT = -26.0207535;
    private static final double CAMPUS_LON = 28.1336694;

    private static class VerifiedIncident extends Incident {
        final long reportedAt;
        VerifiedIncident(IncidentType type, String area, int hours, double lat, double lon, long time) {
            super(type, type.label + " Reported", area, hours, lat, lon);
            reportedAt = time;
        }
    }

    private OsmMapHelper map;
    private EditText searchInput;
    private TextView mapAttribution;
    private ImageView incidentIcon;
    private TextView incidentTitle, incidentLocation, incidentReported;

    /** Chip -> the incident type it filters to (null for the "All" chip). */
    private final Map<TextView, IncidentType> chips = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crime_map);


        searchInput = findViewById(R.id.searchInput);
        mapAttribution = findViewById(R.id.mapAttribution);
        incidentIcon = findViewById(R.id.incidentIcon);
        incidentTitle = findViewById(R.id.incidentTitle);
        incidentLocation = findViewById(R.id.incidentLocation);
        incidentReported = findViewById(R.id.incidentReported);

        map = new OsmMapHelper(this, (ViewGroup) findViewById(R.id.mapContainer));
        map.setOnIncidentTapped(this::selectIncident);

        setUpChips();
        setUpSearch();
        setUpTimeFilter();
        setUpLayersButton();
        updateAttribution();

        applyFilters();
    }

    // Only public, rounded coordinates are returned by the database function.
    private void loadVerifiedIncidents() {
        if (loading) return;
        SharedPreferences prefs = getSharedPreferences("edusafe_supabase_session", Context.MODE_PRIVATE);
        String token = prefs.getString("access_token", "");
        if (token == null || token.isEmpty() || System.currentTimeMillis() >= prefs.getLong("expires_at", 0)) {
            if (!warnedAboutSession) {
                warnedAboutSession = true;
                incidentTitle.setText("Please sign in again");
                incidentLocation.setText("Your Supabase session is missing or expired.");
                incidentReported.setVisibility(View.GONE);
            }
            return;
        }
        warnedAboutSession = false;
        loading = true;
        Request request = new Request.Builder()
                .url(SupabaseConfig.SUPABASE_URL + "/rest/v1/rpc/get_public_map_incidents")
                .header("apikey", SupabaseConfig.SUPABASE_KEY)
                .header("Authorization", "Bearer " + token)
                .get().build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    loading = false;
                    showLoadError("Could not connect to Supabase. Check internet.");
                });
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response res = response) {
                    if (!res.isSuccessful()) {
                        int code = res.code();
                        runOnUiThread(() -> {
                            loading = false;
                            showLoadError("Map request failed (HTTP " + code + "). Check login and RPC permissions.");
                        });
                        return;
                    }
                    JSONArray rows = new JSONArray(res.body() == null ? "[]" : res.body().string());
                    List<Incident> fresh = new ArrayList<>();
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject row = rows.getJSONObject(i);
                        IncidentType type;
                        try { type = IncidentType.valueOf(row.getString("incident_type").toUpperCase(java.util.Locale.US)); }
                        catch (Exception ignored) { type = IncidentType.OTHER; }
                        long created;
                        try { created = OffsetDateTime.parse(row.getString("created_at")).toInstant().toEpochMilli(); }
                        catch (Exception ignored) { created = System.currentTimeMillis(); }
                        int hours = (int) Math.min(Integer.MAX_VALUE,
                                Math.max(0L, (System.currentTimeMillis() - created) / 3600000L));
                        double lat = row.getDouble("latitude");
                        double lon = row.getDouble("longitude");
                        fresh.add(new VerifiedIncident(type, approximateArea(lat, lon),
                                hours, lat, lon, created));
                    }
                    runOnUiThread(() -> {
                        loading = false;
                        if (isFinishing() || isDestroyed()) return;
                        // Preserve selection across refreshes, rather than jumping every 5 seconds.
                        VerifiedIncident previous = selected instanceof VerifiedIncident
                                ? (VerifiedIncident) selected : null;
                        allIncidents.clear();
                        allIncidents.addAll(fresh);
                        selected = null;
                        if (previous != null) {
                            for (Incident candidate : fresh) {
                                VerifiedIncident next = (VerifiedIncident) candidate;
                                if (next.reportedAt == previous.reportedAt
                                        && next.latitude == previous.latitude
                                        && next.longitude == previous.longitude
                                        && next.type == previous.type) {
                                    selected = next;
                                    break;
                                }
                            }
                        }
                        boolean initialData = !hasLoadedIncidents;
                        hasLoadedIncidents = true;
                        applyFilters();
                        // Keep the initial map centered on campus; do not automatically
                        // fly to an unrelated test incident in California.
                        if (initialData) firstLoad = false;
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        loading = false;
                        showLoadError("Could not read incident data.");
                    });
                }
            }
        });
    }

    private void showLoadError(String message) {
        if (!hasLoadedIncidents) {
            incidentTitle.setText("Unable to load incidents");
            incidentLocation.setText(message);
            incidentReported.setVisibility(View.GONE);
        }
    }

    private String approximateArea(double lat, double lon) {
        float[] result = new float[1];
        Location.distanceBetween(lat, lon, CAMPUS_LAT, CAMPUS_LON, result);
        if (result[0] <= 2000) return "Near Eduvos Campus (approximate)";
        return String.format(Locale.US, "Approximate area: %.3f, %.3f", lat, lon);
    }

    private String distanceFromStudent(Incident incident) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return "Distance unavailable (location permission needed)";
        }
        try {
            LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (manager == null) return "Distance unavailable";
            Location best = null;
            for (String provider : manager.getProviders(true)) {
                Location location = manager.getLastKnownLocation(provider);
                if (location != null && (best == null || location.getTime() > best.getTime())) {
                    best = location;
                }
            }
            if (best == null || System.currentTimeMillis() - best.getTime() > 10 * 60 * 1000L)
                return "Distance unavailable (waiting for GPS)";
            float[] distance = new float[1];
            Location.distanceBetween(best.getLatitude(), best.getLongitude(),
                    incident.latitude, incident.longitude, distance);
            if (distance[0] < 1000)
                return Math.round(distance[0] / 100f) * 100 + " m away (approx.)";
            return String.format(Locale.getDefault(), "%.1f km away (approx.)",
                    distance[0] / 1000.0);
        } catch (SecurityException | IllegalArgumentException e) {
            return "Distance unavailable";
        }
    }

    // ---------------------------------------------------------------
    // Filtering
    // ---------------------------------------------------------------

    private void applyFilters() {
        shown = IncidentRepository.filter(allIncidents, selectedType,
                searchInput.getText().toString(), maxHoursAgo);
        map.showIncidents(shown);

        Incident before = selected;
        if (selected == null || !shown.contains(selected)) {
            selected = shown.isEmpty() ? null : shown.get(0);
        }
        showSelected();

        // slide to the new selection, but not on the very first load (the map already starts on the campus)
        // Move the map only when the student taps a pin, not during polling.
        // An incident may be thousands of kilometres from the campus.
        firstLoad = false;
    }

    private void selectIncident(Incident incident) {
        selected = incident;
        showSelected();
        map.centerOn(incident);
    }

    // ---------------------------------------------------------------
    // The incident card
    // ---------------------------------------------------------------

    private void showSelected() {
        if (selected == null) {
            incidentIcon.setImageResource(IncidentType.OTHER.glyphRes);
            incidentIcon.setBackgroundTintList(ColorStateList.valueOf(0xFF9AA3B2));
            incidentTitle.setText("No incidents found");
            incidentLocation.setText("Try a different filter or search.");
            incidentReported.setVisibility(View.GONE);
            return;
        }
        incidentIcon.setImageResource(selected.type.glyphRes);
        incidentIcon.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(this, selected.type.colorRes)));
        incidentTitle.setText(selected.title);
        incidentLocation.setText(selected.location + "\n" + distanceFromStudent(selected));
        if (selected instanceof VerifiedIncident) {
            long time = ((VerifiedIncident) selected).reportedAt;
            String day = android.text.format.DateUtils.isToday(time) ? "Today"
                    : new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(new Date(time));
            String clock = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(time));
            incidentReported.setText(day + " at " + clock + "  •  Verified by Campus Security");
        } else {
            incidentReported.setText(selected.reportedText());
        }
        incidentReported.setVisibility(View.VISIBLE);
    }

    // ---------------------------------------------------------------
    // Filter chips
    // ---------------------------------------------------------------

    private void setUpChips() {
        chips.put((TextView) findViewById(R.id.chipAll), null);
        chips.put((TextView) findViewById(R.id.chipRobbery), IncidentType.ROBBERY);
        chips.put((TextView) findViewById(R.id.chipTheft), IncidentType.THEFT);
        chips.put((TextView) findViewById(R.id.chipAssault), IncidentType.ASSAULT);
        chips.put((TextView) findViewById(R.id.chipOther), IncidentType.OTHER);

        for (Map.Entry<TextView, IncidentType> entry : chips.entrySet()) {
            final IncidentType type = entry.getValue();
            entry.getKey().setOnClickListener(v -> {
                selectedType = type;
                styleChips();
                applyFilters();
            });
        }
        styleChips();
    }

    /** The chosen chip is navy with white text; the others are white with navy text. */
    private void styleChips() {
        for (Map.Entry<TextView, IncidentType> entry : chips.entrySet()) {
            boolean on = entry.getValue() == selectedType;
            TextView chip = entry.getKey();
            chip.setBackgroundResource(on ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            chip.setTextColor(ContextCompat.getColor(this, on ? R.color.white : R.color.navy));
        }
    }

    // ---------------------------------------------------------------
    // Search
    // ---------------------------------------------------------------

    private void setUpSearch() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { applyFilters(); }
        });
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                return true;
            }
            return false;
        });
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        searchInput.clearFocus();
    }

    // ---------------------------------------------------------------
    // The sliders button: how recent?
    // ---------------------------------------------------------------

    private void setUpTimeFilter() {
        findViewById(R.id.filterButton).setOnClickListener(anchor -> {
            PopupMenu popup = new PopupMenu(this, anchor);
            Menu menu = popup.getMenu();
            MenuItem anyTime = menu.add(0, 0, 0, "All time");
            MenuItem lastDay = menu.add(0, 1, 1, "Last 24 hours");
            MenuItem lastWeek = menu.add(0, 2, 2, "Last 7 days");
            menu.setGroupCheckable(0, true, true);

            if (maxHoursAgo == 24) lastDay.setChecked(true);
            else if (maxHoursAgo == 7 * 24) lastWeek.setChecked(true);
            else anyTime.setChecked(true);

            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == 1) maxHoursAgo = 24;
                else if (id == 2) maxHoursAgo = 7 * 24;
                else maxHoursAgo = Integer.MAX_VALUE;
                applyFilters();
                return true;
            });
            popup.show();
        });
    }

    // ---------------------------------------------------------------
    // The layers button: satellite <-> street
    // ---------------------------------------------------------------

    private void setUpLayersButton() {
        findViewById(R.id.layersButton).setOnClickListener(v -> {
            map.setSatellite(!map.isSatellite());
            updateAttribution();
            Toast.makeText(this, map.isSatellite() ? "Satellite view" : "Street map", Toast.LENGTH_SHORT).show();
        });
    }

    private void updateAttribution() {
        mapAttribution.setText(map.isSatellite()
                ? "Imagery \u00a9 Esri, Maxar, Earthstar Geographics"
                : "\u00a9 OpenStreetMap contributors");
    }

    // ---------------------------------------------------------------
    // Map lifecycle
    // ---------------------------------------------------------------

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
        mapActive = true;
        refreshHandler.removeCallbacks(refreshTask);
        refreshTask.run();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapActive = false;
        refreshHandler.removeCallbacks(refreshTask);
        map.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        refreshHandler.removeCallbacks(refreshTask);
        map.onDestroy();
    }
}

