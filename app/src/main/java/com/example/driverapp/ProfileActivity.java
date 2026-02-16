package com.example.driverapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.HashMap;

public class ProfileActivity extends AppCompatActivity {

    SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        TextView tvName = findViewById(R.id.tvProfileName);
        TextView tvNik = findViewById(R.id.tvProfileNik);
        Button btnLogout = findViewById(R.id.btnLogout);

        // 1. Ambil Data Sesi
        session = new SessionManager(this);
        HashMap<String, String> user = session.getUserDetails();

        String name = user.get(SessionManager.KEY_NAMA);
        String nik = user.get(SessionManager.KEY_NIK); // Pastikan di SessionManager ada KEY_NIK

        // 2. Tampilkan
        tvName.setText(name);
        tvNik.setText("NIK: " + (nik != null ? nik : "-"));

        // 3. Logika Logout
        btnLogout.setOnClickListener(v -> {
            session.logoutUser(); // Fungsi bawaan SessionManager (pastikan ada method clear editor)

            // Arahkan ke Welcome Activity dan HAPUS history stack
            Intent i = new Intent(ProfileActivity.this, WelcomeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
        });
    }
}