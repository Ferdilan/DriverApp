package com.example.driverapp;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class RegisterActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;

    // Variabel untuk menangani Upload File
    private ValueCallback<Uri[]> mUploadMessage;
    private final static int FILECHOOSER_RESULTCODE = 1;

    // GANTI URL INI DENGAN IP LAPTOP ANDA (JANGAN LOCALHOST)
    // Contoh: "http://192.168.1.10:3000/driver/register"
    private static final String REGISTER_URL = "http://192.168.100.133:3000/register";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progressBar);

        setupWebView();

        // Load URL
        webView.loadUrl(REGISTER_URL);

        // Agar tombol Back HP mengontrol back di Browser dulu, baru keluar aplikasi
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Cek apakah WebView bisa mundur halaman?
                if (webView.canGoBack()) {
                    webView.goBack(); // Mundur di browser
                } else {
                    // Jika sudah mentok di halaman awal, tutup Activity (keluar)
                    finish();
                    // Atau: remove(); // untuk mematikan callback ini dan membiarkan sistem menangani back
                }
            }
        });
    }

    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true); // Wajib aktifkan JS
        webSettings.setDomStorageEnabled(true); // Agar form modern berjalan lancar
        webSettings.setAllowFileAccess(true);

        // 1. WebViewClient: Agar link tetap dibuka di dalam aplikasi (bukan lempar ke Chrome)
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);

                // OPTIMASI: Deteksi jika sudah sukses register
                // Misal web Anda redirect ke halaman "/login" atau "/success" setelah daftar
                if (url.contains("/login") || url.contains("/success")) {
                    // Tutup Activity ini dan kembali ke Login Native Android
                    finish();
                }
            }
        });

        // 2. WebChromeClient: KUNCI UNTUK UPLOAD FILE & PROGRESS BAR
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress == 100) {
                    progressBar.setVisibility(View.GONE);
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                }
            }

            // Method ini dipanggil saat user klik <input type="file"> di HTML
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (mUploadMessage != null) {
                    mUploadMessage.onReceiveValue(null);
                }
                mUploadMessage = filePathCallback;

                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*"); // Hanya gambar (bisa diganti "*/*")

                startActivityForResult(Intent.createChooser(intent, "Pilih Foto Dokumen"), FILECHOOSER_RESULTCODE);
                return true;
            }
        });
    }

    // Menangani hasil pemilihan file dari Galeri
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data); // Penting

        if (requestCode == FILECHOOSER_RESULTCODE) {
            if (null == mUploadMessage) return;

            Uri result = (data == null || resultCode != Activity.RESULT_OK) ? null : data.getData();
            if (result != null) {
                mUploadMessage.onReceiveValue(new Uri[]{result});
            } else {
                mUploadMessage.onReceiveValue(null);
            }
            mUploadMessage = null;
        }
    }


}