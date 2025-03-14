package com.example.proyecto_divisa.data

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

interface ExchangeRateApiService {
    @GET("v6/{apiKey}/latest/{baseCurrency}")
    suspend fun getLatestRates(
        @Path("apiKey") apiKey: String,
        @Path("baseCurrency") baseCurrency: String
    ): ExchangeRateResponse
}

data class ExchangeRateResponse(
    val result: String, // "success" o "error"
    val base_code: String, // Moneda base (ej: "USD")
    @SerializedName("time_last_update_unix") val timeLastUpdateUtc: Long,
    val conversion_rates: Map<String, Double> // Tasas de cambio
)

object RetrofitClient {
    private const val BASE_URL = "https://v6.exchangerate-api.com/"

    val instance: ExchangeRateApiService by lazy {
        val interceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ExchangeRateApiService::class.java)
    }
}