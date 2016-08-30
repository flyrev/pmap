package com.egrworks.pmap;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.view.DefaultClusterRenderer;
import com.google.maps.android.ui.IconGenerator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, LocationListener, View.OnClickListener {
    private static final String TAG = "MapsActivity";

    private static final int MY_PERMISSIONS_REQUEST_FINE_LOCATION = 123;

    private GoogleMap mMap;
    private LocationManager mLocationManager;
    private IconGenerator mIconGenerator;

    private ClusterManager<Pokemon> mClusterManager;
    private List<Pokemon> mPokemons = new ArrayList<>();

    private Button mClearButton;
    private Button mReloadButton;

    private TickUpdateTask mTickUpdateTask;

    private static String[] POKEMON_NAMES = null;
    public static String getPokemonNameForId(int id) {
        if (id < 1 ||  id > POKEMON_NAMES.length) return Integer.toString(id);
        String result = POKEMON_NAMES[id-1];
        if (result.isEmpty()) return Integer.toString(id);
        else return result;
    }

    private class PokemonRenderer extends DefaultClusterRenderer<Pokemon> {
        public PokemonRenderer(Context context, GoogleMap map, ClusterManager<Pokemon> clusterManager) {
            super(context, map, clusterManager);
        }

        @Override
        protected void onBeforeClusterItemRendered(Pokemon item, MarkerOptions markerOptions) {
            markerOptions.icon(BitmapDescriptorFactory.fromAsset("icons/" + item.id + ".png"));
            markerOptions.title(item.getTitle());

            if (item.label == null) {
                mIconGenerator.setRotation(180);
                mIconGenerator.setContentRotation(180);
                Bitmap bm = mIconGenerator.makeIcon(item.getRemainingTimeString());
                item.label = mMap.addMarker(new MarkerOptions()
                        .icon(BitmapDescriptorFactory.fromBitmap(bm))
                        .position(item.getPosition())
                        .anchor(mIconGenerator.getAnchorU(), mIconGenerator.getAnchorV()));
            }
            item.label.setVisible(true);
        }

        @Override
        protected void onBeforeClusterRendered(Cluster<Pokemon> cluster, MarkerOptions markerOptions) {
            Collection<Pokemon> pokemons = cluster.getItems();
            for (Pokemon p : pokemons) if (p.label != null) p.label.setVisible(false);
            super.onBeforeClusterRendered(cluster, markerOptions);
        }
    }

    private class TickUpdateTask extends AsyncTask<Void, Void, Void> {
        private boolean running = true;
        private List<Pokemon> toRemove = new ArrayList<>();
        @Override
        protected Void doInBackground(Void... params) {
            try {
                while (running) {
                    publishProgress();
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                // Ignore
            }
            return null;
        }

        public void stop() {
            running = false;
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            for (Pokemon p : mPokemons) {
                if (p.getRemainingTime() <= 0) {
                    if (p.label != null) p.label.remove();
                    mClusterManager.removeItem(p);
                    toRemove.add(p);
                } else if (p.label != null && p.label.isVisible()) {
                    mIconGenerator.setRotation(180);
                    mIconGenerator.setContentRotation(180);
                    Bitmap bm = mIconGenerator.makeIcon(p.getRemainingTimeString());
                    p.label.setIcon(BitmapDescriptorFactory.fromBitmap(bm));
                    p.label.setTitle(p.getRemainingTimeString());
                }
            }
            if (toRemove.size() > 0) {
                mPokemons.removeAll(toRemove);
                toRemove.clear();
                mClusterManager.cluster();
            }
        }
    }

    private class LiveDownloadTask extends AsyncTask<Void, Void, List<Pokemon>> {
        @Override
        protected void onPreExecute() {
            mTickUpdateTask.stop();
            mTickUpdateTask = null;
        }

        @Override
        protected List<Pokemon> doInBackground(Void... params) {
            ConnectivityManager connMgr = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected()) {
                try {
                    URL url = new URL("http://www.pokemonmap.no/live");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setReadTimeout(10000);
                    conn.setConnectTimeout(15000);
                    conn.setRequestMethod("GET");
                    conn.setDoInput(true);
                    conn.connect();
                    int response = conn.getResponseCode();

                    Log.d(TAG, "Response code: " + response);
                    if (response != 200) return null;

                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    String line;
                    StringBuilder contentBuffer = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        contentBuffer.append(line);
                    }
                    byte[] content = Base64.decode(contentBuffer.toString(), Base64.NO_WRAP);
                    reader.close();


                    List<Pokemon> result = new ArrayList<>(content.length/10);
                    try {
                        for (int i = 0; i < content.length; i += 10) {
                            result.add(ConversionTools.decompress(content, i));
                        }
                    } catch (IndexOutOfBoundsException e) {
                        // ignore
                    }
                    return result;

                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());

                }
            } else {
                Toast.makeText(MapsActivity.this, "No internet connection", Toast.LENGTH_SHORT).show();
            }
            return null;
        }

        @Override
        protected void onPostExecute(List<Pokemon> pokemons) {
            if (pokemons != null) {
                mPokemons.clear();
                mClusterManager.clearItems();
                mMap.clear();

                int counter = 0;
                LatLngBounds bounds = mMap.getProjection().getVisibleRegion().latLngBounds;
                for (Pokemon p : pokemons) {
                    if (bounds.contains(p.getPosition())) {
                        mClusterManager.addItem(p);
                        mPokemons.add(p);
                        //mMap.addMarker(p.getMarkerOptions());
                        counter++;
                    }
                }
                Log.d(TAG, "Added " + counter + " pokemons");
            }
            mReloadButton.setEnabled(true);
            mClusterManager.cluster();
            mTickUpdateTask = new TickUpdateTask();
            mTickUpdateTask.execute();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mClearButton = (Button)findViewById(R.id.buttonClear);
        mReloadButton = (Button)findViewById(R.id.buttonReload);
        mClearButton.setOnClickListener(this);
        mReloadButton.setOnClickListener(this);

        mIconGenerator = new IconGenerator(this);

        if (POKEMON_NAMES == null)
            POKEMON_NAMES = getResources().getStringArray(R.array.pokemon_names);

        int permissionCheck = ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION);

        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_FINE_LOCATION);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mTickUpdateTask = new TickUpdateTask();
        mTickUpdateTask.execute();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mTickUpdateTask != null) mTickUpdateTask.stop();
        mTickUpdateTask = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mClusterManager = new ClusterManager<>(this, mMap);
        mClusterManager.setRenderer(new PokemonRenderer(this, mMap, mClusterManager));
        mMap.setOnCameraIdleListener(mClusterManager);
        int res = this.checkCallingOrSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION);
        if (res == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Criteria c = new Criteria();
            mLocationManager.requestLocationUpdates(30000L, 5.0f, c, this, null);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 10);
        if (mMap != null) mMap.animateCamera(cameraUpdate);
        try {
            mLocationManager.removeUpdates(this);
        } catch (SecurityException e) {
            // Ignore
        }
    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onClick(View v) {
        if (v.getId() == mClearButton.getId()) {
            mMap.clear();
        } else if (v.getId() == mReloadButton.getId()) {
            mReloadButton.setEnabled(false);
            LiveDownloadTask ldt = new LiveDownloadTask();
            ldt.execute();
        }
    }
}
