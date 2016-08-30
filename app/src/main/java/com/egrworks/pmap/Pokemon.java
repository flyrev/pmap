package com.egrworks.pmap;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.maps.android.clustering.ClusterItem;

import java.util.Calendar;

public class Pokemon implements ClusterItem {
    public final int id;
    public final long despawnTime;
    private final LatLng latLng;
    public Marker label;

    public Pokemon(double lat, double lon, int id, long despawnTime) {
        this.id = id;
        this.despawnTime = despawnTime;
        latLng = new LatLng(lat, lon);
    }

    public int getRemainingTime() {
        Calendar now = Calendar.getInstance();
        return (int)(despawnTime - now.getTimeInMillis())/1000;
    }

    public String getRemainingTimeString() {
        int total = getRemainingTime();
        if (total <= 0) return "00:00";
        int seconds = total % 60;
        int minutes = (total - seconds) / 60;
        return (minutes > 9 ? minutes : "0" + minutes) + ":" + (seconds > 9 ? seconds : "0" + seconds);
    }

    public String getTitle() {
        return MapsActivity.getPokemonNameForId(id);
    }

    @Override
    public LatLng getPosition() {
        return latLng;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof Pokemon)) {
            return false;
        } else {
            Pokemon p = (Pokemon)o;
            return (id == p.id && despawnTime == p.despawnTime && latLng.equals(p.getPosition()));
        }
    }
}
