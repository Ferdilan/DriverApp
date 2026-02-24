package com.example.driverapp.model;

import com.google.gson.annotations.SerializedName;

public class DriverLoginResponse {
    @SerializedName("success")
    boolean success;

    @SerializedName("message")
    String message;

    @SerializedName("data")
    DriverData data;

    // Getter methods...
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public DriverData getData() { return data; }

    public class DriverData {
        @SerializedName("id_ambulans")
        int id;
        @SerializedName("nama_driver")
        String nama;

        public int getId() { return id; }
        public String getNama() { return nama; }
    }
}