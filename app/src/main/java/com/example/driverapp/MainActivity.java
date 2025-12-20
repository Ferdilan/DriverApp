package com.example.driverapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
//import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private SessionManager session;
    private Spinner spinnerStatus;
    private boolean isMapReady = false;
    private static final int PERMISSIONS_REQUEST_CODE = 100;
//    private SessionManager session;

    // Pilihan Status sesuai Enum Database
    private String[] statusOptions = {"OFFLINE (Tidak Aktif)", "AVAILABLE (Siaga)", "BUSY (Sibuk)"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            android.content.pm.ApplicationInfo appInfo = getPackageManager().getApplicationInfo(getPackageName(), android.content.pm.PackageManager.GET_META_DATA);
            Bundle bundle = appInfo.metaData;

            if (bundle != null) {
                String apiKey = bundle.getString("com.google.android.geo.API_KEY");
                Log.e("CEK_KEY", "KUNCI YANG DIBACA APLIKASI: " + apiKey);
            } else {
                Log.e("CEK_KEY", "Bundle Metadata KOSONG/NULL");
            }
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            Log.e("CEK_KEY", "Gagal membaca paket aplikasi", e);
        } catch (NullPointerException e) {
            Log.e("CEK_KEY", "Error Null Pointer", e);
        }

        session = new SessionManager(getApplicationContext());
        if (!session.isLoggedIn()) {
            goToLogin();
            return;
        }

        // Setup UI
        TextView tvWelcome = findViewById(R.id.tvWelcome);
        tvWelcome.setText("Halo, " + session.getDriverName());

        Button btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> logout());

        setupSpinner();
        setupMap();

        // Cek Izin Lokasi saat pertama buka
        checkPermissions();
    }

    private void setupMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapHome);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void setupSpinner() {
        spinnerStatus = findViewById(R.id.spinnerStatus);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, statusOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStatus.setAdapter(adapter);

        // Set status awal berdasarkan Service berjalan atau tidak
        if (LocationService.isServiceRunning) {
            spinnerStatus.setSelection(1); // AVAILABLE
        } else {
            spinnerStatus.setSelection(0); // OFFLINE
        }

        spinnerStatus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                handleStatusChange(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void handleStatusChange(int position) {
        switch (position) {
            case 0: // OFFLINE
                stopLocationService();
                Toast.makeText(this, "Anda OFFLINE. Tidak akan menerima order.", Toast.LENGTH_SHORT).show();
                break;
            case 1: // AVAILABLE
                if (checkPermissions()) {
                    startLocationService();
                    Toast.makeText(this, "Status: SIAGA (Menunggu Order)", Toast.LENGTH_SHORT).show();
                } else {
                    spinnerStatus.setSelection(0); // Balik ke offline jika izin ditolak
                }
                break;
            case 2: // BUSY
                // Biasanya status ini otomatis dari sistem, tapi driver bisa set manual jika istirahat
                // Tetap nyalakan service tracking, tapi server tidak akan kasih order baru
                if (checkPermissions()) startLocationService();
                Toast.makeText(this, "Status: SIBUK (Tracking Tetap Jalan)", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        isMapReady = true;
        updateMapUI();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                // Jika diizinkan, otomatis set ke AVAILABLE dan nyalakan service
                spinnerStatus.setSelection(1);
                startLocationService();
                updateMapUI(); // Munculkan Blue Dot
            } else {
                // Jika ditolak, paksa ke OFFLINE
                Toast.makeText(this, "Izin Lokasi diperlukan!", Toast.LENGTH_LONG).show();
                spinnerStatus.setSelection(0);
            }
        }
    }

    private void updateMapUI() {
        if (mMap == null) return;
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                mMap.setMyLocationEnabled(true); // Blue Dot Muncul!

                // Fokus awal ke Malang (Bisa diganti lokasi terakhir driver)
                LatLng malang = new LatLng(-7.9666, 112.6326);
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(malang, 12));
            }
        } catch (SecurityException e) {
            Log.e("Map", "Izin Lokasi Error: " + e.getMessage());
        }
    }

    private void startLocationService() {
        if (!LocationService.isServiceRunning) {
            Intent serviceIntent = new Intent(this, LocationService.class);
            serviceIntent.putExtra("DRIVER_ID", session.getDriverId());
            ContextCompat.startForegroundService(this, serviceIntent);
        }
    }

    private void stopLocationService() {
        if (LocationService.isServiceRunning) {
            Intent serviceIntent = new Intent(this, LocationService.class);
            stopService(serviceIntent);
        }
    }

    private boolean checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            return false;
        }
        return true;
    }

    private void logout() {
        stopLocationService();
        session.logoutUser();
        goToLogin();
    }

    private void goToLogin(){
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
    }