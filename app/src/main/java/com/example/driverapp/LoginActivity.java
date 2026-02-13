package com.example.driverapp;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin, btnRegisterWeb;
    private ProgressBar progressBar;
    private SessionManager session;

    // GANTI IP INI DENGAN IP KOMPUTER ANDA YANG MENJALANKAN 'ambulance-admin'
    // Jangan gunakan 'localhost' di Android Emulator, gunakan '10.0.2.2' atau IP LAN asli
    private static final String LOGIN_URL = "http://192.168.0.101:3000/api/driver/login";
    private static final String REGISTER_URL = "http://192.168.100.133:3000/driver/register";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. CEK SESI TERLEBIH DAHULU (Fitur Login Sekali)
        session = new SessionManager(getApplicationContext());
        if (session.isLoggedIn()) {
            // Jika sudah login, langsung lempar ke MainActivity
            goToHome();
            return; // Hentikan proses onCreate
        }

        setContentView(R.layout.activity_login);

        // Inisialisasi UI
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegisterWeb = findViewById(R.id.btnRegisterWeb);
        progressBar = findViewById(R.id.progressBar);

        // Aksi Tombol Login
        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString();
            String pass = etPassword.getText().toString();
            if (!email.isEmpty() && !pass.isEmpty()) {
                new LoginTask().execute(email, pass);
            } else {
                Toast.makeText(this, "Email dan Password harus diisi", Toast.LENGTH_SHORT).show();
            }
        });

        // Aksi Tombol Daftar (Buka Browser)
        btnRegisterWeb.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(REGISTER_URL));
            startActivity(browserIntent);
        });
    }

    private void goToHome() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        // Flag ini mencegah user kembali ke login page saat tekan tombol Back
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    // Tugas Async untuk menghubungi API Backend
    private class LoginTask extends AsyncTask<String, Void, String> {
        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
            btnLogin.setEnabled(false);
        }

        @Override
        protected String doInBackground(String... params) {
            String email = params[0];
            String password = params[1];
            try {
                URL url = new URL(LOGIN_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                conn.setDoOutput(true);

                // Buat JSON Body
                JSONObject jsonParam = new JSONObject();
                jsonParam.put("email", email);
                jsonParam.put("password", password);

                // Kirim Data
                OutputStream os = conn.getOutputStream();
                os.write(jsonParam.toString().getBytes("UTF-8"));
                os.close();

                // Baca Respon
                int code = conn.getResponseCode();
                BufferedReader in;
                if (code == 200) {
                    in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                } else {
                    in = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                }

                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();
                return response.toString();

            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            progressBar.setVisibility(View.GONE);
            btnLogin.setEnabled(true);

            if (result != null) {
                try {
                    JSONObject jsonResponse = new JSONObject(result);
                    boolean success = jsonResponse.getBoolean("success");

                    if (success) {
                        // 2. SIMPAN SESI SAAT LOGIN BERHASIL
                        JSONObject data = jsonResponse.getJSONObject("data");
                        int id = data.getInt("id_ambulans");
                        String nama = data.getString("nama_driver");
                        String kategori = data.optString("kategori", "UMUM");

                        session.createLoginSession(id, nama, kategori);
                        Toast.makeText(LoginActivity.this, "Selamat Datang, " + nama, Toast.LENGTH_SHORT).show();

                        goToHome();
                    } else {
                        String msg = jsonResponse.optString("message", "Login Gagal");
                        Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(LoginActivity.this, "Error memproses data", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(LoginActivity.this, "Gagal terhubung ke server", Toast.LENGTH_SHORT).show();
            }
        }
    }
}