package com.example.driverapp;

import android.util.Log;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MqttClientManager {

    private static final String TAG = "MqttClientManager";
    private static MqttClientManager instance;
    private Mqtt3AsyncClient client;

    // Ambil Konfigurasi dari BuildConfig (local.properties)
    private static final String SERVER_HOST = BuildConfig.MQTT_HOST;
    private static final int SERVER_PORT = 1883;
    private static final String USERNAME = BuildConfig.MQTT_USERNAME;
    private static final String PASSWORD = BuildConfig.MQTT_PASSWORD;

    private MqttClientManager() {
        // Private Constructor
    }

    public static synchronized MqttClientManager getInstance() {
        if (instance == null) {
            instance = new MqttClientManager();
        }
        return instance;
    }

    public boolean isConnected() {
        return client != null && client.getState().isConnected();
    }

    // --- CONNECT ---
    public interface ConnectionListener {
        void onSuccess();
        void onError(String errorMessage);
    }

    public void connect(ConnectionListener listener) {
        if (isConnected()) {
            if (listener != null) listener.onSuccess();
            return;
        }

        String clientId = "Driver_" + UUID.randomUUID().toString().substring(0, 8);

        client = MqttClient.builder()
                .useMqttVersion3()
                .identifier(clientId)
                .serverHost(SERVER_HOST)
                .serverPort(SERVER_PORT)
                .automaticReconnectWithDefaultConfig()
                .buildAsync();

        client.connectWith()
                .simpleAuth()
                .username(USERNAME)
                .password(PASSWORD.getBytes(StandardCharsets.UTF_8))
                .applySimpleAuth()
                .cleanSession(true)
                .keepAlive(60)
                .send()
                .whenComplete((connAck, throwable) -> {
                    if (throwable != null) {
                        Log.e(TAG, "Gagal Connect: " + throwable.getMessage());
                        if (listener != null) listener.onError(throwable.getMessage());
                    } else {
                        Log.d(TAG, "BERHASIL CONNECT ke " + SERVER_HOST);
                        if (listener != null) listener.onSuccess();
                    }
                });
    }

    // Interface Callback Pesan
    public interface MessageListener {
        void onMessage(String topic, String message);
    }

    public void subscribe(String topic, MessageListener listener) {
        if (client == null) return;

        client.subscribeWith()
                .topicFilter(topic)
                .callback(publish -> {
                    String message = new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8);
                    if (listener != null) {
                        listener.onMessage(topic, message);
                    }
                })
                .send()
                .whenComplete((subAck, throwable) -> {
                    if (throwable != null) {
                        Log.e(TAG, "Gagal Subscribe: " + topic);
                    } else{
                        Log.d(TAG, "Sukses Subscribe: " + topic);
                    }
                });
    }

    // --- PUBLISH ---
    public void publish(String topic, String message) {
        if (!isConnected()) return;

        client.publishWith()
                .topic(topic)
                .payload(message.getBytes(StandardCharsets.UTF_8))
                .send()
                .whenComplete((publish, throwable) -> {
                    if (throwable != null){
                        Log.e(TAG, "Gagal Publish ke " + topic);
                    } else {
                        Log.d(TAG, "Terkirim ke " + topic);
                    }
                });
    }

    public void disconnect() {
        if (client != null) {
            client.disconnect();
        }
    }
}