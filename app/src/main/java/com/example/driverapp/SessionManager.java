package com.example.driverapp;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private SharedPreferences pref;
    private SharedPreferences.Editor editor;
    private Context context;

    // Mode Shared Preferences (0 = Private)
    int PRIVATE_MODE = 0;

    // Nama file shared preferences
    private static final String PREF_NAME = "AmbulanceDriverPref";

    // Kunci data
    private static final String IS_LOGIN = "IsLoggedIn";
    public static final String KEY_ID = "id_ambulans";
    public static final String KEY_NAMA = "nama_driver";
    public static final String KEY_KATEGORI = "kategori";

    // Constructor
    public SessionManager(Context context) {
        this.context = context;
        pref = context.getSharedPreferences(PREF_NAME, PRIVATE_MODE);
        editor = pref.edit();
    }

    /**
     * Buat sesi login (Simpan data driver ke HP)
     */
    public void createLoginSession(int id, String nama, String kategori){
        editor.putBoolean(IS_LOGIN, true);
        editor.putInt(KEY_ID, id);
        editor.putString(KEY_NAMA, nama);
        editor.putString(KEY_KATEGORI, kategori);
        editor.commit(); // Simpan perubahan
    }

    /**
     * Cek status login user
     * @return true jika sudah login, false jika belum
     */
    public boolean isLoggedIn(){
        return pref.getBoolean(IS_LOGIN, false);
    }

    /**
     * Ambil ID Driver yang sedang login (berguna untuk MQTT nanti)
     */
    public int getDriverId(){
        return pref.getInt(KEY_ID, -1);
    }

    /**
     * Hapus sesi (Logout)
     */
    public void logoutUser(){
        editor.clear();
        editor.commit();
    }

    public String getDriverName() {
        return pref.getString(KEY_NAMA, "Driver");
    }
}
