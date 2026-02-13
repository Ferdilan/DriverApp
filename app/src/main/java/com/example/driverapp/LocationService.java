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

import org.jetbrains.annotations.Nullable;
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

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private MqttClientManager mqttManager;
    private int driverId = -1; // Akan diisi dari Intent

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        mqttManager = MqttClientManager.getInstance();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                for (Location location : locationResult.getLocations()) {
                    publishLocationToCloud(location);
                }
            }
        };
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("DRIVER_ID")) {
            driverId = intent.getIntExtra("DRIVER_ID", -1);
            Log.d(TAG, "Service Start. Driver ID: " + driverId);
        }

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
        // Cek dulu apakah sudah konek (agar tidak double connect)
        if (mqttManager.isConnected()) {
            subscribeToTasks();
            return;
        }

        // PERUBAHAN: Gunakan connect() tanpa parameter user/pass (sudah di Manager)
        mqttManager.connect(new MqttClientManager.ConnectionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Service: MQTT Connected");
                subscribeToTasks();
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Service: MQTT Connect Error -> " + errorMessage);
            }
        });
    }

    private void subscribeToTasks() {
        // PERUBAHAN: Subscribe tugas spesifik untuk driver ini
        if (driverId != -1) {
            String topicTugas = "ambulans/tugas/" + driverId;

            mqttManager.subscribe(topicTugas, (topic, message) -> {
                Log.d(TAG, "Tugas Pribadi Masuk: " + message);
                showIncomingOrderUI(message);
            });
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

            // Tambahkan Bearing (Arah) agar rotasi mobil di peta pasien mulus
            payload.put("bearing", location.getBearing());

            String topic = "ambulans/lokasi/update/" + driverId;

            // Kirim via Manager Anda
            // QoS 0 = Fire and forget (Cepat)
            mqttManager.publish(topic, payload.toString());

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
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }

        // Opsional: Matikan MQTT saat service mati
        if (mqttManager != null) {
            mqttManager.disconnect();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}