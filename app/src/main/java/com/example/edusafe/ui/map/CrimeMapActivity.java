package com.example.edusafe.ui.map;

import android.content.res.ColorStateList;
import android.os.Bundle;
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

    private final List<Incident> allIncidents = com.example.edusafe.ui.map.IncidentRepository.all();
    private List<Incident> shown = new ArrayList<>();

    private IncidentType selectedType = null;          // null means "All"
    private int maxHoursAgo = Integer.MAX_VALUE;       // any time
    private Incident selected;
    private boolean firstLoad = true;

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
        if (!firstLoad && selected != null && selected != before) map.centerOn(selected);
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
        incidentLocation.setText(selected.location);
        incidentReported.setText(selected.reportedText());
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
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        map.onDestroy();
    }
}

