package com.example.edusafe.ui.map;

/** One reported safety incident (sample data for now). */
public class Incident {

    public final IncidentType type;
    public final String title;
    public final String location;   // a place name people recognise, e.g. "Alsatian Road"
    public final int hoursAgo;
    public final double latitude;
    public final double longitude;

    public Incident(IncidentType type, String title, String location, int hoursAgo,
                    double latitude, double longitude) {
        this.type = type;
        this.title = title;
        this.location = location;
        this.hoursAgo = hoursAgo;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /** "Reported 2 hours ago", "Reported yesterday", "Reported 3 days ago". */
    public String reportedText() {
        if (hoursAgo < 1) return "Reported less than an hour ago";
        if (hoursAgo == 1) return "Reported 1 hour ago";
        if (hoursAgo < 24) return "Reported " + hoursAgo + " hours ago";
        int days = hoursAgo / 24;
        if (days == 1) return "Reported yesterday";
        return "Reported " + days + " days ago";
    }
}
