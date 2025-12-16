package com.example.driverapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSIONS_REQUEST_CODE = 100;
    private TextView tvWelcome, tvStatusLabel;
    private SwitchCompat switchStatus;
    private Button btnLogout;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        session = new SessionManager(getApplicationContext());
        if (!session.isLoggedIn()) {
            goToLogin();
            return;
        }

        tvWelcome = findViewById(R.id.tvWelcome);
        tvStatusLabel = findViewById(R.id.tvStatusLabel);
        switchStatus = findViewById(R.id.switchStatus);
        btnLogout = findViewById(R.id.btnLogout);

        tvWelcome.setText("Halo, " + session.getDriverName());

        // Cek apakah service sudah berjalan (agar status switch sinkron saat app dibuka ulang)
        if (LocationService.isServiceRunning) {
            switchStatus.setChecked(true);
            updateUIOnline();
        } else {
            switchStatus.setChecked(false);
            updateUIOffline();
        }

        switchStatus.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (checkAndRequestPermissions()) {
                    startLocationService();
                } else {
                    buttonView.setChecked(false);
                }
            } else {
                stopLocationService();
            }
        });

        btnLogout.setOnClickListener(v -> logout());
    }

    private void startLocationService() {
        if (!LocationService.isServiceRunning) {
            Intent serviceIntent = new Intent(this, LocationService.class);
            serviceIntent.putExtra("DRIVER_ID", session.getDriverId());
            ContextCompat.startForegroundService(this, serviceIntent);
            updateUIOnline();
        }
    }

    private void stopLocationService() {
        if (LocationService.isServiceRunning) {
            Intent serviceIntent = new Intent(this, LocationService.class);
            stopService(serviceIntent);
            updateUIOffline();
        }
    }

    private void updateUIOnline() {
        tvStatusLabel.setText("SIAGA (ONLINE)");
        tvStatusLabel.setTextColor(Color.parseColor("#4CAF50"));
    }

    private void updateUIOffline() {
        tvStatusLabel.setText("OFFLINE");
        tvStatusLabel.setTextColor(Color.parseColor("#F44336"));
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

    private boolean checkAndRequestPermissions() {
        List<String> listPermissionsNeeded = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        // Izin Notifikasi (Android 13+)
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[0]), PERMISSIONS_REQUEST_CODE);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
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
                switchStatus.setChecked(true); // Otomatis nyalakan switch jika izin diberi
            } else {
                Toast.makeText(this, "Izin Lokasi & Notifikasi diperlukan!", Toast.LENGTH_LONG).show();
                switchStatus.setChecked(false);
            }
        }
    }
}