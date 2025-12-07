package com.example.medfinder

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface OpenRouteServiceApi {
    @GET("v2/directions/driving-car")
    fun getDrivingDirections(
        @Header("Authorization") apiKey: String,
        @Query("start") start: String, // Format: "lon,lat"
        @Query("end") end: String      // Format: "lon,lat"
    ): Call<OpenRouteServiceResponse>
}