package com.example.driverapp;



import android.content.Context;

import android.util.Log;



import org.eclipse.paho.android.service.MqttAndroidClient;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;

import org.eclipse.paho.client.mqttv3.IMqttToken;

import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;

import org.eclipse.paho.client.mqttv3.MqttException;

import org.eclipse.paho.client.mqttv3.MqttMessage;



public class MqttClientManager {

    private static final String TAG = "MqttClientManager";

    private static MqttClientManager instance;

    private MqttAndroidClient client;

    private String topicToSubscribe;


// Definisikan sebuah interface untuk callback pesan

    public interface MqttMessageListener {

        void onMessageReceived(String topic, MqttMessage message);

    }

    private MqttMessageListener listener;


    private MqttClientManager() {

// Konstruktor privat untuk mencegah instansiasi langsung (Singleton Pattern)

    }


    public static synchronized MqttClientManager getInstance() {

        if (instance == null) {

            instance = new MqttClientManager();

        }

        return instance;

    }


    public void setListener(MqttMessageListener listener) {

        this.listener = listener;

    }


    public void setSubscriptionTopic(String topic) {

        this.topicToSubscribe = topic;

    }


    public void connect(Context context, String brokerUri, String clientId, String username, String password) {

        Log.d(TAG, "Mencoba menghubungkan ke broker: " + brokerUri);

        try {

            if (client == null || !client.isConnected()) {

                client = new MqttAndroidClient(context.getApplicationContext(), brokerUri, clientId);


// Atur callback utama untuk semua event

                client.setCallback(new MqttCallbackExtended() {

                    @Override

                    public void connectComplete(boolean reconnect, String serverURI) {

                        Log.d(TAG, "Koneksi ke broker berhasil. Server URI: " + serverURI);

// Di sini Anda bisa otomatis subscribe ke topik default jika perlu

                        if (topicToSubscribe != null && !topicToSubscribe.isEmpty()) {

                            subscribe(topicToSubscribe, 1);

                        }

                    }


                    @Override

                    public void connectionLost(Throwable cause) {

                        Log.e(TAG, "Koneksi ke broker terputus.", cause);

                    }


                    @Override

                    public void messageArrived(String topic, MqttMessage message) throws Exception {

                        Log.i(TAG, "Pesan diterima dari topik: " + topic);

                        if (listener != null) {

// Teruskan pesan ke listener yang terdaftar (misal: MainActivity)

                            listener.onMessageReceived(topic, message);

                        }

                    }


                    @Override

                    public void deliveryComplete(IMqttDeliveryToken token) {

// Tidak perlu aksi khusus untuk saat ini

                    }

                });


// Konfigurasi opsi koneksi

                MqttConnectOptions options = new MqttConnectOptions();

                options.setAutomaticReconnect(true);

                options.setCleanSession(true); // Mulai sesi baru setiap kali terhubung

                options.setUserName(username); // Jika broker Anda menggunakan autentikasi

                options.setPassword(password.toCharArray()); // Jika broker Anda menggunakan autentikasi


                IMqttToken token = client.connect(options);


                token.setActionCallback(new IMqttActionListener() {

                    @Override

                    public void onSuccess(IMqttToken asyncActionToken) {

                        Log.d(TAG, "IMqttActionListener: Koneksi berhasil.");

                    }


                    @Override

                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {

                        Log.e(TAG, "IMqttActionListener: Koneksi gagal.", exception);

                    }

                });

            }

        } catch (MqttException e) {

            Log.e(TAG, "Error saat koneksi MQTT", e);

        }

    }


    public void publish(String topic, String payload, int qos) {

        if (client == null || !client.isConnected()) {

            Log.w(TAG, "Tidak dapat mempublikasikan, klien tidak terhubung.");

            return;

        }

        try {

            MqttMessage message = new MqttMessage(payload.getBytes());

            message.setQos(qos); // Quality of Service: 0, 1, or 2

            client.publish(topic, message, null, new IMqttActionListener() {

                @Override

                public void onSuccess(IMqttToken asyncActionToken) {

                    Log.d(TAG, "Pesan berhasil dipublikasikan ke topik: " + topic);

                }


                @Override

                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {

                    Log.e(TAG, "Gagal mempublikasikan pesan ke topik: " + topic, exception);

                }

            });

        } catch (MqttException e) {

            Log.e(TAG, "Error saat mempublikasikan pesan", e);

        }

    }


    public void subscribe(String topic, int qos) {

        if (client == null || !client.isConnected()) {
            Log.w(TAG, "Tidak dapat berlangganan, klien tidak terhubung.");
        }
        try {
            client.subscribe(topic, qos, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "SUKSES Subscribe ke: " + topic);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "GAGAL Subscribe ke: " + topic, exception);
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, "Error saat berlangganan", e);
        }
    }

    public void disconnect() {
        if (client != null && client.isConnected()) {
            try {
                client.disconnect();
                Log.d(TAG, "Koneksi diputus.");
            } catch (MqttException e) {
                Log.e(TAG, "Error saat memutus koneksi", e);
            }
        }
    }
}