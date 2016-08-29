package com.egrworks.pmap;

import android.content.Context;
import android.content.pm.PackageManager;
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
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, LocationListener, View.OnClickListener {
    private static final String TAG = "MapsActivity";

    private static final int MY_PERMISSIONS_REQUEST_FINE_LOCATION = 123;

    private GoogleMap mMap;
    private LocationManager mLocationManager;

    private Button mClearButton;
    private Button mReloadButton;

    private class GetAssetsTask extends AsyncTask<Void, Void, Void> {

        private InputStream getLocalAssets(int id) throws IOException {
            return MapsActivity.this.getAssets().open("icons/" + id + ".gif");
        }

        private InputStream getRemoteStream(int id) throws IOException {
            ConnectivityManager connMgr = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected()) {
                String urlString = "http://www.pokemonmap.no/assets/pokemon_gif_static/" + id + ".gif";
                URL url = new URL(urlString);
                Log.d(TAG, "Downloading: " + urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setReadTimeout(10000);
                conn.setConnectTimeout(15000);
                conn.setRequestMethod("GET");
                conn.setDoInput(true);
                conn.connect();
                int response = conn.getResponseCode();
                if (response != 200) {
                    Log.e(TAG, "Could not download id: " + id);
                    throw new IOException("Return code: " + response);
                }
                return conn.getInputStream();
            } else {
                throw new IOException("Network is not available");
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            String[] files = fileList();
            for (int i = 1; i <= 328; i++) {
                try {
                    boolean fileExists = false;
                    for (String f : files) {
                        if (f.equals(i + ".gif")) {
                            fileExists = true;
                            break;
                        }
                    }
                    if (fileExists) continue;
                    InputStream in;
                    try {
                        in = getLocalAssets(i);
                    } catch (IOException e) {
                        Log.e(TAG, "Could not extract " + i + ".gif from local storage");
                        Log.i(TAG, "Trying remove");
                        in = getRemoteStream(i);
                    }

                    FileOutputStream out = openFileOutput(i + ".gif", MODE_PRIVATE);
                    int len;
                    byte[] buffer = new byte[2048];
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                    in.close();
                    out.close();
                } catch (IOException e) {
                    Log.e(TAG, "Could not extract " + i + ".gif");
                    deleteFile(i + ".gif");
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            mClearButton.setEnabled(true);
            mReloadButton.setEnabled(true);
        }
    }

    private class LiveDownloadTask extends AsyncTask<Void, Void, List<Pokemon>> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
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


                    List<Pokemon> result = new ArrayList<>();

                    // Here is parsing of the input
                    Set<String> filesSet = new HashSet<>();
                    Collections.addAll(filesSet, fileList());
                    try {
                        for (int i = 0; i < content.length; i += 10) {
                            double lat = ConversionTools.long2Double(ConversionTools.bytes2Long(content, i));
                            double lng = ConversionTools.long2Double(ConversionTools.bytes2Long(content, i + 4));
                            int id = content[i + 8] & 0xff;
                            int spawnTime = content[i + 9] & 0xff;
                            boolean hasIcon = filesSet.contains(id + ".gif");
                            result.add(new Pokemon(lat, lng, id, spawnTime, hasIcon));
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
                mMap.clear();
                int counter = 0;
                LatLngBounds bounds = mMap.getProjection().getVisibleRegion().latLngBounds;
                for (Pokemon p : pokemons) {
                    if (bounds.contains(p.getLatLng())) {
                        mMap.addMarker(p.getMarkerOptions());
                        counter++;
                    }
                }
                Log.d(TAG, "Added " + counter + " pokemons");
            }
            mReloadButton.setEnabled(true);
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
        mClearButton.setEnabled(false);
        mReloadButton.setEnabled(false);

        //IconDownloadTask downloadTask = new IconDownloadTask();
        //downloadTask.execute();
        GetAssetsTask getAssetsTask = new GetAssetsTask();
        getAssetsTask.execute();


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
