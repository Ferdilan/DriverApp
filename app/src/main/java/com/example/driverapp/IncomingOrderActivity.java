package com.example.driverapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONException;
import org.json.JSONObject;

public class IncomingOrderActivity extends AppCompatActivity {

    private TextView tvNamaPasien, tvJarak;
    private MqttClientManager mqttManager;
    private SessionManager session;

    // Variabel data
    private String idPanggilan;
    private double latPasien = 0, lonPasien = 0;
    private String namaPasienStr = "Pasien";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_incoming_order);

        mqttManager = MqttClientManager.getInstance();
        session = new SessionManager(this);

        tvNamaPasien = findViewById(R.id.tvNamaPasien);
        tvJarak = findViewById(R.id.tvJarak);
        Button btnTerima = findViewById(R.id.btnTerima);
        Button btnTolak = findViewById(R.id.btnTolak);

        // Ambil Data dari Service
        String jsonData = getIntent().getStringExtra("ORDER_DATA");
        if (jsonData == null) jsonData = getIntent().getStringExtra("PAYLOAD");

        parseData(jsonData);

        btnTerima.setOnClickListener(v -> {
            kirimKonfirmasi("diterima");

            // 1. Beritahu Server: Driver ini sekarang SIBUK
            updateOperationalStatus("Busy");

            finish(); // Tutup halaman incoming agar tidak bisa back
        });

        btnTolak.setOnClickListener(v -> {
            kirimKonfirmasi("ditolak");
            finish();
        });
    }

    private void parseData(String json) {
        if (json == null) return;
        try {
            JSONObject data = new JSONObject(json);

            if (data.has("id_panggilan")){
                idPanggilan = data.getString("id_panggilan");
            } else{
                idPanggilan = data.getString("call_id");
            }

            // =================================================================
            // MODE: DATA ASLI (PRODUCTION)
            // Mengambil data sesungguhnya yang dikirim oleh Server/Pasien
            // =================================================================

            namaPasienStr = data.optString("nama_pasien", "Pasien Darurat");
            String jarak = data.optString("jarak", "Menghitung jarak...");

            // Ambil koordinat dari JSON.
            // Angka default (-7.983...) hanya dipakai jika server mengirim data kosong/null.
            latPasien = data.optDouble("lokasi_pasien_lat", -7.983908);
            lonPasien = data.optDouble("lokasi_pasien_lon", 112.621391);

            // Update Tampilan UI
            tvNamaPasien.setText(namaPasienStr);
            tvJarak.setText(jarak);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void kirimKonfirmasi(String status) {
        if (idPanggilan == null || idPanggilan.isEmpty()) {
            Toast.makeText(this, "Error: Data Panggilan Invalid", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject resp = new JSONObject();

            resp.put("id_ambulans", session.getDriverId());
            resp.put("id_panggilan", idPanggilan);
            resp.put("status", status);

            mqttManager.publish("ambulans/respons/konfirmasi", resp.toString());

            if (status.equals("diterima")) {
                Toast.makeText(this, "Tugas Diterima! Buka Peta...", Toast.LENGTH_SHORT).show();

                // Pastikan data dilempar ke Navigasi dengan benar
                Intent intent = new Intent(IncomingOrderActivity.this, NavigationActivity.class);
                intent.putExtra("LAT_PASIEN", latPasien);
                intent.putExtra("LON_PASIEN", lonPasien);
                intent.putExtra("NAMA_PASIEN", namaPasienStr);
                intent.putExtra("ID_PANGGILAN", idPanggilan);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
            finish();

        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error JSON: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Method untuk mengirim update status ke Server/Database
    private void updateOperationalStatus(String statusString) {
        // GANTI URL INI DENGAN ALAMAT SERVER ANDA
        // Pastikan endpoint backend untuk update status driver sudah ada
        String url = "http://192.168.100.133:3000/api/driver/status";

        // 2. Format Data ("busy" -> "Busy") untuk menghindari error ENUM database
//        String statusFinal = statusRaw.substring(0, 1).toUpperCase() + statusRaw.substring(1).toLowerCase();
        // Siapkan Body JSON
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("id_ambulans", session.getDriverId()); // ID Driver/Ambulans
            jsonBody.put("id_panggilan", idPanggilan); // ID Panggilan (dari server)
            jsonBody.put("status", statusString); // 'offline', 'available', atau 'busy'
        } catch (Exception e) { e.printStackTrace(); }

        // Buat Request
        okhttp3.RequestBody body = okhttp3.RequestBody.create(
                jsonBody.toString(),
                okhttp3.MediaType.parse("application/json; charset=utf-8")
        );

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .put(body) // Biasanya update menggunakan PUT atau POST (Tergantung Backend)
                .build();

        // Eksekusi di Background
        new Thread(() -> {
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
            try {
                okhttp3.Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    Log.d("UpdateStatus", "Sukses update ke DB: " + statusString);
                    // Opsional: Update UI di thread utama jika perlu
                } else {
                    Log.e("UpdateStatus", "Gagal update: " + response.code());
                }
            } catch (Exception e) {
                Log.e("UpdateStatus", "Error koneksi", e);
            }
        }).start();
    }
}