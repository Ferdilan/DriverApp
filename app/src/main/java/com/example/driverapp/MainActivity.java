package com.example.driverapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "MainActivityDriver";
    private GoogleMap mMap;
    private SessionManager session;
    private Spinner spinnerStatus;

    // --- 1. Tambahkan Variabel MQTT ---
    private MqttClientManager mqttManager;

    private static final int PERMISSIONS_REQUEST_CODE = 100;

    // Pilihan Status sesuai Enum Database
    private String[] statusOptions = {"OFFLINE (Tidak Aktif)", "AVAILABLE (Siaga)", "BUSY (Sibuk)"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Cek Metadata API Key (Opsional, untuk debug)
        try {
            android.content.pm.ApplicationInfo appInfo = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = appInfo.metaData;
            if (bundle != null) {
                String apiKey = bundle.getString("com.google.android.geo.API_KEY");
                Log.d("CEK_KEY", "API Key Found: " + (apiKey != null));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        session = new SessionManager(getApplicationContext());
        if (!session.isLoggedIn()) {
            goToLogin();
            return;
        }

        // --- 2. Inisialisasi MQTT Manager ---
        mqttManager = MqttClientManager.getInstance();

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

        // Set status awal
        if (LocationService.isServiceRunning) {
            spinnerStatus.setSelection(1); // AVAILABLE
            // Jika service jalan, berarti harusnya kita konek MQTT juga
            connectAndSubscribeOrder();
        } else {
            spinnerStatus.setSelection(0); // OFFLINE
        }

        spinnerStatus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Hindari trigger saat inisialisasi awal
                handleStatusChange(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void handleStatusChange(int position) {
        String statusToSend = "offline"; // Default

        switch (position) {
            case 0: // OFFLINE
                statusToSend = "offline";
                stopLocationService();

                // --- 3. Putus Koneksi MQTT saat Offline ---
                if (mqttManager != null) mqttManager.disconnect();

                Toast.makeText(this, "Anda OFFLINE", Toast.LENGTH_SHORT).show();
                break;

            case 1: // AVAILABLE
                statusToSend = "available";
                if (checkPermissions()) {
                    startLocationService();

                    // --- 4. Konek & Subscribe saat Available ---
                    connectAndSubscribeOrder();

                    Toast.makeText(this, "Status: SIAGA (Online)", Toast.LENGTH_SHORT).show();
                } else {
                    spinnerStatus.setSelection(0);
                    return;
                }
                break;

            case 2: // BUSY
                statusToSend = "busy";
                // Tetap nyalakan service tracking
                if (checkPermissions()) startLocationService();

                // Saat Busy, kita tetap konek MQTT (untuk kirim lokasi),
                // tapi mungkin server tidak akan kirim order baru.
                if (!mqttManager.isConnected()) connectAndSubscribeOrder();

                Toast.makeText(this, "Status: SIBUK", Toast.LENGTH_SHORT).show();
                break;
        }
        updateOperationalStatus(statusToSend);
    }

    // --- 5. Logika Koneksi & Terima Order ---
    private void connectAndSubscribeOrder() {
        if (mqttManager.isConnected()) {
            subscribeToOrders(); // Jika sudah konek, langsung subscribe
            return;
        }

        mqttManager.connect(new MqttClientManager.ConnectionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "MQTT Connected");
                runOnUiThread(() -> {
                    // Update UI jika perlu (misal ikon sinyal hijau)
                    subscribeToOrders();
                });
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "MQTT Error: " + errorMessage);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Gagal Konek Server", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void subscribeToOrders() {

        String topicOrder = "ambulans/tugas/";
        mqttManager.subscribe(topicOrder, (topic, message) -> {
            Log.d(TAG, "Order Masuk: " + message);
            processIncomingOrder(message);
        });
    }

    private void processIncomingOrder(String jsonPayload) {
        try {
            JSONObject json = new JSONObject(jsonPayload);

            // Ambil data penting
            int idPasien = json.optInt("id_pasien", -1);
            double latPasien = json.optDouble("lokasi_pasien_lat", 0.0);
            double lonPasien = json.optDouble("lokasi_pasien_lon", 0.0);
            String jenisLayanan = json.optString("jenis_layanan", "Transport");

            // Buka Activity Pop-up Order
            Intent intent = new Intent(MainActivity.this, IncomingOrderActivity.class);
            intent.putExtra("ID_PASIEN", idPasien);
            intent.putExtra("LAT_PASIEN", latPasien);
            intent.putExtra("LON_PASIEN", lonPasien);
            intent.putExtra("JENIS_LAYANAN", jenisLayanan);

            // Flag agar activity muncul di atas meskipun aplikasi sedang diminimize (opsional)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            startActivity(intent);

        } catch (JSONException e) {
            Log.e(TAG, "JSON Error: " + e.getMessage());
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
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
                spinnerStatus.setSelection(1);
                startLocationService();
                connectAndSubscribeOrder(); // Konek MQTT juga
                updateMapUI();
            } else {
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
                mMap.setMyLocationEnabled(true);
                // Default ke lokasi Malang (Bisa diganti)
                LatLng malang = new LatLng(-7.9666, 112.6326);
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(malang, 12));
            }
        } catch (SecurityException e) {
            Log.e("Map", "Izin Lokasi Error: " + e.getMessage());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true; // Harus return true agar menu muncul
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_profile) {
            // Buka Halaman Profil
            startActivity(new Intent(this, ProfileActivity.class));
            return true;
        } else if (id == R.id.menu_history) {
            // Buka Halaman Riwayat
//            startActivity(new Intent(this, HistoryActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
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
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS_REQUEST_CODE);
            return false;
        }
        return true;
    }

    private void logout() {
        stopLocationService();
        if (mqttManager != null) mqttManager.disconnect();
        session.logoutUser();
        goToLogin();
    }

    private void goToLogin(){
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void updateOperationalStatus(String statusString) {
        // GANTI URL SESUAI IP ANDA (Pastikan Laptop & HP di Wifi sama)
        String url = "http://192.168.0.104:3000/api/driver/status";

        // Format huruf besar ("available" -> "Available") agar sesuai ENUM DB
        String statusFinal = statusString.substring(0, 1).toUpperCase() + statusString.substring(1).toLowerCase();

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("id_ambulans", session.getDriverId());
            jsonBody.put("status", statusFinal);
        } catch (Exception e) { e.printStackTrace(); }

        okhttp3.RequestBody body = okhttp3.RequestBody.create(
                jsonBody.toString(),
                okhttp3.MediaType.parse("application/json; charset=utf-8")
        );

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .put(body)
                .build();

        new Thread(() -> {
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
            try {
                okhttp3.Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    Log.d("UpdateStatus", "Sukses update ke DB: " + statusFinal);
                } else {
                    Log.e("UpdateStatus", "Gagal update: " + response.code());
                }
            } catch (Exception e) {
                Log.e("UpdateStatus", "Error koneksi", e);
            }
        }).start();
    }
}