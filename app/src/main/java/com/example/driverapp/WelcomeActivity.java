package com.example.driverapp;

import android.content.Intent;
import android.os.Bundle;

import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;

import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.driverapp.databinding.ActivityWelcomeBinding;

public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        // 1. CEK SESI (Apakah user sudah pernah login?)
        SessionManager session = new SessionManager(this);
        if (session.isLoggedIn()) {
            goToMain();
            return;
        }

        Button btnLogin = findViewById(R.id.btnWelcomeLogin);
        Button btnRegister = findViewById(R.id.btnWelcomeRegister);

        if (btnLogin == null) {
            Toast.makeText(this, "FATAL ERROR: ID btnWelcomeLogin tidak ditemukan di XML!", Toast.LENGTH_LONG).show();
            return; // Stop agar tidak crash
        }

        if (btnRegister == null) {
            Toast.makeText(this, "FATAL ERROR: ID btnWelcomeRegister tidak ditemukan di XML!", Toast.LENGTH_LONG).show();
            return;
        }

        // Arahkan ke halaman masing-masing
        btnLogin.setOnClickListener(v -> {
            startActivity(new Intent(WelcomeActivity.this, LoginActivity.class));
        });

        btnRegister.setOnClickListener(v -> {
            startActivity(new Intent(WelcomeActivity.this, RegisterActivity.class));
        });
    }

    private void  goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}