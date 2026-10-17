package com.example.edusafe.ui.map;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;

import com.example.edusafe.R;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.io.File;
import java.util.List;

/**
 * OsmMapHelper
 * ----------------------------------------------------------------------
 * EVERYTHING that touches the map library (osmdroid) lives in this one
 * file. The rest of the Crime Map screen only talks to the small public
 * methods below, so the map engine could be swapped (e.g. for Google Maps)
 * by rewriting just this class.
 *
 * Two looks, switched by setSatellite():
 *   satellite: Esri World Imagery (matches the aerial reference design)
 *   street:    OpenStreetMap
 * ----------------------------------------------------------------------
 */
public class OsmMapHelper {

    public interface OnIncidentTapped {
        void onIncidentTapped(Incident incident);
    }

    private static final double START_ZOOM = 17.0;
    private static final GeoPoint START_CENTER = new GeoPoint(-26.0213, 28.1340); // just around the campus

    /** Aerial imagery. The tile URL puts y before x, which is how Esri's server expects it. */
    private static final OnlineTileSourceBase ESRI_IMAGERY = new XYTileSource(
            "EsriWorldImagery", 0, 19, 256, ".jpg",
            new String[]{"https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"}) {
        @Override
        public String getTileURLString(long pMapTileIndex) {
            return getBaseUrl()
                    + MapTileIndex.getZoom(pMapTileIndex) + "/"
                    + MapTileIndex.getY(pMapTileIndex) + "/"
                    + MapTileIndex.getX(pMapTileIndex);
        }
    };

    private final Context context;
    private final MapView mapView;
    private boolean satellite = true;
    private OnIncidentTapped tapListener;

    /** Creates the map and puts it inside container (behind any other views already in there). */
    public OsmMapHelper(Context context, ViewGroup container) {
        this.context = context;

        // Required by the map library: say who we are, and keep its tile cache inside the app's own cache folder
        IConfigurationProvider config = Configuration.getInstance();
        config.setUserAgentValue(context.getPackageName());
        File base = new File(context.getCacheDir(), "osmdroid");
        config.setOsmdroidBasePath(base);
        config.setOsmdroidTileCache(new File(base, "tiles"));

        mapView = new MapView(context);
        mapView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        container.addView(mapView, 0);

        mapView.setMultiTouchControls(true); // pinch to zoom, drag to move
        mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        mapView.setMinZoomLevel(14.0);
        mapView.setMaxZoomLevel(19.0);
        applyTileSource();

        mapView.getController().setZoom(START_ZOOM);
        mapView.getController().setCenter(START_CENTER);
    }

    public void setOnIncidentTapped(OnIncidentTapped listener) {
        this.tapListener = listener;
    }

    /** Replaces everything on the map with the campus pin plus these incidents. */
    public void showIncidents(List<Incident> incidents) {
        mapView.getOverlays().clear();

        Marker campus = new Marker(mapView);
        campus.setPosition(new GeoPoint(IncidentRepository.CAMPUS_LATITUDE, IncidentRepository.CAMPUS_LONGITUDE));
        campus.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        campus.setIcon(drawable(R.drawable.ic_pin_campus));
        campus.setTitle("Eduvos Midrand Campus");
        mapView.getOverlays().add(campus);

        for (final Incident incident : incidents) {
            Marker marker = new Marker(mapView);
            marker.setPosition(new GeoPoint(incident.latitude, incident.longitude));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setIcon(drawable(incident.type.pinRes));
            marker.setInfoWindow(null); // the info card below the map replaces the default speech bubble
            marker.setOnMarkerClickListener(new Marker.OnMarkerClickListener() {
                @Override
                public boolean onMarkerClick(Marker m, MapView mv) {
                    if (tapListener != null) tapListener.onIncidentTapped(incident);
                    return true;
                }
            });
            mapView.getOverlays().add(marker);
        }
        mapView.invalidate();
    }

    /** Slides the map so this incident is in the middle. */
    public void centerOn(Incident incident) {
        mapView.getController().animateTo(new GeoPoint(incident.latitude, incident.longitude));
    }

    public boolean isSatellite() {
        return satellite;
    }

    public void setSatellite(boolean satellite) {
        this.satellite = satellite;
        applyTileSource();
        mapView.invalidate();
    }

    private void applyTileSource() {
        mapView.setTileSource(satellite ? ESRI_IMAGERY : TileSourceFactory.MAPNIK);
    }

    private Drawable drawable(int resId) {
        return ContextCompat.getDrawable(context, resId);
    }

    // The map needs to be told about the screen's lifecycle
    public void onResume() { mapView.onResume(); }
    public void onPause() { mapView.onPause(); }
    public void onDestroy() { mapView.onDetach(); }
}
