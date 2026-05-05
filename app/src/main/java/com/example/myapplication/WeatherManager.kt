package com.example.myapplication

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import com.example.myapplication.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request

object WeatherManager {
    private const val API_KEY = BuildConfig.WEATHER_API_KEY
    private const val BASE_URL = "https://api.openweathermap.org/data/2.5/weather"

    data class WeatherData(
        val temp: Double,
        val feelsLike : Double,
        val description: String,
        val humidity: Int,
        val windSpeed: Double
    )

    suspend fun getWeather(latitude: Double, longitude: Double): WeatherData? {
        return withContext(Dispatchers.IO){
            try{
                // 1. Tạo URL
                val urlString = "$BASE_URL?lat=$latitude&lon=$longitude&appid=$API_KEY&units=metric&lang=vi"

                // 2. Gọi API (dùng OkHttp)
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(urlString)
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    Log.e("WEATHER_API", "API Error: ${response.code}")
                    return@withContext null
                }

                val jsonString = response.body?.string() ?: return@withContext null
                return@withContext parseWeatherResponse(jsonString)

            }catch(e: Exception){
                Log.e("WEATHER_API", "Exception: ${e.message}")
                return@withContext null
            }
        }

    }
    private fun parseWeatherResponse(json: String): WeatherData {
        val jsonObj = JSONObject(json)

        // Lấy dữ liệu từ JSON
        val mainObj = jsonObj.getJSONObject("main")
        val temp = mainObj.getDouble("temp")
        val feelsLike = mainObj.getDouble("feels_like")
        val humidity = mainObj.getInt("humidity")


        val weatherArray = jsonObj.getJSONArray("weather")
        val description = weatherArray.getJSONObject(0).getString("description")

        val windObj = jsonObj.getJSONObject("wind")
        val windSpeed = windObj.getDouble("speed")

        return WeatherData(
            temp = temp,
            feelsLike = feelsLike,
            description = description,
            humidity = humidity,
            windSpeed = windSpeed
        )


    }
}


























