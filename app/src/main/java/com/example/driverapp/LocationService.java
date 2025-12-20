package com.example.driverapp;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.json.JSONObject;

public class LocationService extends Service {

    public static boolean isServiceRunning = false;
    private static final String TAG = "LocationService";
    private static final String CHANNEL_ID = "DriverLocationChannel";
    private static final int NOTIFICATION_ID = 123;

    // --- KONFIGURASI MQTT CLOUD (Isi sesuai akun HiveMQ/EMQX Anda) ---
    private static final String MQTT_BROKER_URL = BuildConfig.MQTT_HOST;
    private static final String MQTT_USERNAME = BuildConfig.MQTT_USERNAME;
    private static final String MQTT_PASSWORD = BuildConfig.MQTT_PASSWORD;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private MqttClientManager mqttManager;
    private int driverId = -1; // Akan diisi dari Intent

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // 1. Panggil Singleton Manager Anda
        mqttManager = MqttClientManager.getInstance();

        // 2. Siapkan Callback GPS
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                for (Location location : locationResult.getLocations()) {
                    // Setiap dapat lokasi, kirim via Manager
                    publishLocationToCloud(location);
                }
            }
        };

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 3. Ambil ID Driver
        if (intent != null && intent.hasExtra("DRIVER_ID")) {
            driverId = intent.getIntExtra("DRIVER_ID", -1);
            Log.d(TAG, "Service Start. Driver ID: " + driverId);
        }

        // 4. Jalankan Notifikasi Foreground (Wajib)
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Driver Online")
                .setContentText("Mengirim lokasi ke pusat...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(NOTIFICATION_ID, notification);

        // 5. Konek ke MQTT Cloud via Manager Anda
        connectToMqttCloud();

        // 6. Mulai Ambil GPS
        startLocationUpdates();

        isServiceRunning = true;
        return START_STICKY;
    }

    private void connectToMqttCloud() {
        // Buat Client ID Unik
        String clientId = "DriverApp_" + driverId + "_" + System.currentTimeMillis();

        // Panggil fungsi connect dari MqttClientManager Anda
        mqttManager.connect(
                this,
                MQTT_BROKER_URL,
                clientId,
                MQTT_USERNAME,
                MQTT_PASSWORD
        );

        // Opsional: Set Listener jika nanti butuh terima order (T6)
        mqttManager.setListener((topic, message) -> {
            String payload = new String(message.getPayload());

            Log.d(TAG, "Pesan masuk: " + message.toString());

            // Jika topik mengandung 'tugas', berarti ada order masuk!
            if (topic.contains("ambulans/tugas/")) {
                showIncomingOrderUI(payload);
            }
        });

        // Subscribe ke topik tugas pribadi driver
        if(driverId != -1) {
            mqttManager.setSubscriptionTopic("ambulans/tugas/" + driverId);
        }
    }

    // Fungsi untuk membuka layar Popup dari Background Service
    private void showIncomingOrderUI(String jsonPayload) {
        Intent intent = new Intent(this, IncomingOrderActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // Wajib dari Service
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP); // Agar tidak double
        intent.putExtra("PAYLOAD", jsonPayload);
        startActivity(intent);
    }

    private void startLocationUpdates() {
        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 5000) // Update tiap 5 detik
                .setMinUpdateIntervalMillis(3000)
                .build();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }
    }

    private void publishLocationToCloud(Location location) {
        if (driverId == -1) return;

        try {
            // Siapkan Payload JSON
            JSONObject payload = new JSONObject();
            payload.put("lokasi_latitude", location.getLatitude());
            payload.put("lokasi_longitude", location.getLongitude());

            // Tentukan Topik (Sesuai Server service.js Anda)
            // Topik: ambulans/lokasi/update/{id_driver}
            String topic = "ambulans/lokasi/update/" + driverId;

            // Kirim via Manager Anda
            // QoS 0 = Fire and forget (Cepat)
            mqttManager.publish(topic, payload.toString(), 0);

            Log.d(TAG, "Sent Cloud: " + payload.toString());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Driver Location Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isServiceRunning = false;
        fusedLocationClient.removeLocationUpdates(locationCallback);

        // Putus koneksi saat service mati
        mqttManager.disconnect();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}