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

            // --- REKAYASA KOORDINAT UNTUK TESTING SATU HP ---
            // Tujuannya: Agar lokasi pasien berbeda dari lokasi Anda (Driver)
            // Ganti angka ini dengan koordinat yang Anda dapat dari Google Maps tadi!

            // Contoh: Lokasi dummy 1-2 KM dari posisi Anda
            latPasien = -7.9440736; // <--- GANTI INI (Latitude Dummy)
            lonPasien = 112.6145613; // <--- GANTI INI (Longitude Dummy)

            // Override nama agar kita sadar ini data palsu
            namaPasienStr = "Pasien Simulasi (Jarak Dekat)";

            // -----------------------------------------------------

//            namaPasienStr = data.optString("nama_pasien", "Pasien Darurat");
//            String jarak = data.optString("jarak", "- km");

//            latPasien = data.optDouble("lokasi_pasien_lat", -7.983908); // Default Malang
//            lonPasien = data.optDouble("lokasi_pasien_lon", 112.621391);

            tvNamaPasien.setText(namaPasienStr);
//            tvJarak.setText(jarak);

//            Log.d("IncomingOrder", "Parsed ID: " + idPanggilan);
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

            mqttManager.publish("ambulans/respons/konfirmasi", resp.toString(), 1);

            if (status.equals("diterima")) {
                Toast.makeText(this, "Tugas Diterima! Buka Peta...", Toast.LENGTH_SHORT).show();

                // Pastikan data dilempar ke Navigasi dengan benar
                Intent intent = new Intent(IncomingOrderActivity.this, NavigationActivity.class);
                intent.putExtra("LAT_PASIEN", latPasien);
                intent.putExtra("LON_PASIEN", lonPasien);
                intent.putExtra("NAMA_PASIEN", namaPasienStr);
                intent.putExtra("ID_PANGGILAN", idPanggilan);
                startActivity(intent);
            }
            finish();

        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error JSON: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}