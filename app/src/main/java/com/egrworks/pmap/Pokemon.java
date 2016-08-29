package com.egrworks.pmap;

import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

/**
 * Created by egr on 8/28/2016.
 */
public class Pokemon {

    private static final String[] POKEMON_NAMES =
            {

            };

    public final int id;
    public final int spawnTime;
    private final boolean hasIcon;

    private LatLng latLng;
    private MarkerOptions markerOptions = null;

    public Pokemon(double lat, double lon, int id, int spawnTime, boolean hasIcon) {
        this.id = id;
        this.spawnTime = spawnTime;
        this.hasIcon = hasIcon;
        latLng = new LatLng(lat, lon);
    }

    public MarkerOptions getMarkerOptions() {
        if (markerOptions == null) {
            markerOptions = new MarkerOptions();
            int n = ConversionTools.calculateSpawnDiff(spawnTime);
            while (n > 900) n -= 900;
            markerOptions.position(latLng);
            markerOptions.title(getPokemonName(id) + " " + ConversionTools.getTimerString(n));
            if (hasIcon) markerOptions.icon(BitmapDescriptorFactory.fromAsset("icons/" + id + ".gif"));
        }
        return markerOptions;
    }

    public static String getPokemonName(int  id) {
        if (id > POKEMON_NAMES.length || id < 1) return Integer.toString(id);
        return POKEMON_NAMES[id-1];
    }

    public LatLng getLatLng() {
        return latLng;
    }
}
