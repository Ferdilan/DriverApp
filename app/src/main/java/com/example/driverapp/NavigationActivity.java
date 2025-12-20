package com.example.driverapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.PolyUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NavigationActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;

    private double latPasien, lonPasien;
    private String namaPasien, idPanggilan;

    private MqttClientManager mqttManager;
    private SessionManager session;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);

        mqttManager = MqttClientManager.getInstance();
        session = new SessionManager(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Ambil Data dari Intent
        latPasien = getIntent().getDoubleExtra("LAT_PASIEN", 0);
        lonPasien = getIntent().getDoubleExtra("LON_PASIEN", 0);
        namaPasien = getIntent().getStringExtra("NAMA_PASIEN");
        idPanggilan = getIntent().getStringExtra("ID_PANGGILAN");

        TextView tvNama = findViewById(R.id.tvNavNamaPasien);
        tvNama.setText(namaPasien);

        // Inisialisasi Peta
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Tombol Selesai
        Button btnSelesai = findViewById(R.id.btnSelesaiTugas);
        btnSelesai.setOnClickListener(v -> {
            selesaikanTugas();
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Izin Lokasi
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);

            // Ambil Lokasi Driver Saat Ini -> Lalu Minta Rute
            getDriverLocationAndDrawRoute();
        }

        // Tambah Marker Pasien
        LatLng lokasiPasien = new LatLng(latPasien, lonPasien);
        mMap.addMarker(new MarkerOptions()
                .position(lokasiPasien)
                .title("Lokasi Pasien: " + namaPasien)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        // Zoom ke lokasi pasien
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lokasiPasien, 15));
    }

    private void getDriverLocationAndDrawRoute() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                LatLng driverPos = new LatLng(location.getLatitude(), location.getLongitude());
                LatLng pasienPos = new LatLng(latPasien, lonPasien);

                // Tambah Marker
                mMap.addMarker(new MarkerOptions()
                        .position(pasienPos)
                        .title("Pasien: " + namaPasien)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

                // Fokus Kamera agar Driver & Pasien terlihat
                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                builder.include(driverPos);
                builder.include(pasienPos);
                try {
                    mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
                } catch (Exception e) {
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(driverPos, 14));
                }

                fetchRoute(driverPos, pasienPos);
            }
        });
    }

    // --- LOGIKA RUTE DIRECTIONS API ---
    private void fetchRoute(LatLng origin, LatLng dest) {
        executor.execute(() -> {
            String apiKey = null;
            try {
                apiKey = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA)
                        .metaData.getString("com.google.android.geo.API_KEY");
            } catch (Exception e) { e.printStackTrace(); }

            if (apiKey == null) {
                Log.e("Navigation", "API Key TIDAK DITEMUKAN di Manifest");
                return;
            }

            String url = "https://maps.googleapis.com/maps/api/directions/json?" +
                    "origin=" + origin.latitude + "," + origin.longitude +
                    "&destination=" + dest.latitude + "," + dest.longitude +
                    "&key=" + apiKey;

            Log.d("Navigation", "Mengirim Request ke: " + url); // Cek URL di Logcat

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).build();

            try (Response response = client.newCall(request).execute()) {
                String jsonResp = response.body().string();

                // --- PENTING: LIHAT INI DI LOGCAT ---
                Log.d("Navigation", "Response Google: " + jsonResp);
                // ------------------------------------

                JSONObject json = new JSONObject(jsonResp);

                // Cek Status Jawaban Google
                String status = json.optString("status");
                if (!status.equals("OK")) {
                    Log.e("Navigation", "GAGAL GAMBAR RUTE. Status: " + status);
                    Log.e("Navigation", "Pesan Error: " + json.optString("error_message"));
                    return; // Stop di sini
                }

                JSONArray routes = json.getJSONArray("routes");
                if (routes.length() > 0) {
                    String encodedString = routes.getJSONObject(0)
                            .getJSONObject("overview_polyline")
                            .getString("points");

                    List<LatLng> path = PolyUtil.decode(encodedString);

                    handler.post(() -> {
                        mMap.addPolyline(new PolylineOptions()
                                .addAll(path)
                                .color(Color.BLUE)
                                .width(15)); // Pertebal garis jadi 15
                        Log.d("Navigation", "Garis Berhasil Digambar!");
                    });
                } else {
                    Log.e("Navigation", "Rute Kosong (ZERO_RESULTS)");
                }

            } catch (Exception e) {
                Log.e("Navigation", "Error Koneksi/Parsing", e);
            }
        });
    }

    private void selesaikanTugas() {
        try {
            JSONObject resp = new JSONObject();
            resp.put("id_panggilan", idPanggilan);
            resp.put("id_driver", session.getDriverId());
            resp.put("status", "selesai");
            mqttManager.publish("ambulans/respons/konfirmasi", resp.toString(), 1);
            Toast.makeText(this, "Tugas Selesai. Kembali Siaga.", Toast.LENGTH_LONG).show();
            finish();
        } catch (JSONException e) { e.printStackTrace(); }
    }
}