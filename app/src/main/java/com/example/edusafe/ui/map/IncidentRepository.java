package com.example.edusafe.ui.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * IncidentRepository
 * ----------------------------------------------------------------------
 * Sample incidents placed around the real Eduvos Midrand campus
 * (44 Alsatian Road, Glen Austin). Replace all() with a call to the group's
 * backend when there is one. Everything else on the Crime Map keeps working.
 *
 * filter() is plain Java with no Android in it, which keeps it easy to test.
 * ----------------------------------------------------------------------
 */
public final class IncidentRepository {

    /** Eduvos Midrand campus. */
    public static final double CAMPUS_LATITUDE = -26.0207535;
    public static final double CAMPUS_LONGITUDE = 28.1336694;

    private IncidentRepository() { }

    /** All incidents, most recent first. */
    public static List<Incident> all() {
        List<Incident> list = new ArrayList<>();
        list.add(new Incident(IncidentType.ROBBERY, "Attempted Robbery", "Near Eduvos Midrand Campus", 2, -26.02105, 28.13395));
        list.add(new Incident(IncidentType.OTHER, "Suspicious Activity", "Varsity Lodge entrance", 4, -26.01890, 28.13290));
        list.add(new Incident(IncidentType.ROBBERY, "Armed Robbery", "Midrand pedestrian entrance", 5, -26.02235, 28.13290));
        list.add(new Incident(IncidentType.THEFT, "Laptop Theft", "Respublica Princeton Village", 20, -26.01950, 28.13510));
        list.add(new Incident(IncidentType.THEFT, "Phone Snatching", "Near SPARK Midrand High School", 30, -26.02170, 28.13650));
        list.add(new Incident(IncidentType.THEFT, "Theft from Vehicle", "Alsatian Road", 52, -26.02030, 28.13220));
        list.add(new Incident(IncidentType.ASSAULT, "Assault", "Ridge Road", 75, -26.02310, 28.13530));
        Collections.sort(list, new Comparator<Incident>() {
            @Override
            public int compare(Incident a, Incident b) {
                return Integer.compare(a.hoursAgo, b.hoursAgo);
            }
        });
        return list;
    }

    /**
     * @param type       null means "All"
     * @param query      matched against the title and the place name, ignoring case; empty means no search
     * @param maxHoursAgo only incidents reported within this many hours; Integer.MAX_VALUE means any time
     */
    public static List<Incident> filter(List<Incident> source, IncidentType type, String query, int maxHoursAgo) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        List<Incident> result = new ArrayList<>();
        for (Incident incident : source) {
            if (type != null && incident.type != type) continue;
            if (incident.hoursAgo > maxHoursAgo) continue;
            if (!q.isEmpty()
                    && !incident.title.toLowerCase(Locale.getDefault()).contains(q)
                    && !incident.location.toLowerCase(Locale.getDefault()).contains(q)) continue;
            result.add(incident);
        }
        return result;
    }
}
