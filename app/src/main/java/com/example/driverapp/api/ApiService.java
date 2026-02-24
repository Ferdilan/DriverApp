package com.example.driverapp.api;

//import com.example.driverapp.model.HistoryModel;
import com.google.gson.annotations.SerializedName;

import java.util.List;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;

// Interface Retrofit
public interface ApiService {

//    @FormUrlEncoded
//    @POST("pasien/login")
//    Call<com.example.driverapp.api.AuthResponse> login(
//            @Field("nik") String nik,
//            @Field("password") String password
//    );
//
//    @Multipart
//    @POST("pasien/register")
//    Call<com.example.driverapp.api.AuthResponse> register(
//            @Part("nik") RequestBody nik,
//            @Part("nama") RequestBody nama,
//            @Part("tgl_lahir") RequestBody tglLahir,
//            @Part("jenis_kelamin") RequestBody jk,
//            @Part("alamat") RequestBody alamat,
//            @Part("password") RequestBody password,
//            @Part MultipartBody.Part fotoKtp // File Gambar
//    );

//    public class HistoryResponse {
//        @SerializedName("error")
//        boolean error;
//        @SerializedName("data")
//        List<HistoryModel> data;
//
//        public List<HistoryModel> getData() { return data; }
//    }

    // Endpoint GET
//    @GET("transaksi/riwayat/{id}")
//    Call<HistoryResponse> getRiwayat(@Path("id") String idPasien);
}