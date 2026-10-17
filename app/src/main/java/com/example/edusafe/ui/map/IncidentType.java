package com.example.edusafe.ui.map;

import com.example.edusafe.R;

/** The four kinds of incident shown on the Crime Map, each with its own colour, map pin and icon. */
public enum IncidentType {

    ROBBERY("Robbery", R.color.incident_robbery, R.drawable.ic_pin_robbery, R.drawable.ic_glyph_robbery),
    THEFT("Theft", R.color.incident_theft, R.drawable.ic_pin_theft, R.drawable.ic_glyph_theft),
    ASSAULT("Assault", R.color.incident_assault, R.drawable.ic_pin_assault, R.drawable.ic_glyph_assault),
    OTHER("Other", R.color.incident_other, R.drawable.ic_pin_other, R.drawable.ic_glyph_other);

    public final String label;
    public final int colorRes;   // the incident's colour
    public final int pinRes;     // the pin drawn on the map
    public final int glyphRes;   // the white icon shown on the info card

    IncidentType(String label, int colorRes, int pinRes, int glyphRes) {
        this.label = label;
        this.colorRes = colorRes;
        this.pinRes = pinRes;
        this.glyphRes = glyphRes;
    }
}
