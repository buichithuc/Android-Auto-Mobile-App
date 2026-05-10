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
    private const val GOOGLE_API_KEY = BuildConfig.GOOGLE_API_KEY
    private const val BASE_URL = "https://api.openweathermap.org/data/2.5/weather"
    private const val GEOCODING_BASE_URL = "https://maps.googleapis.com/maps/api/geocode/json"

    data class WeatherData(
        val temp: Double,
        val feelsLike : Double,
        val description: String,
        val humidity: Int,
        val windSpeed: Double
    )

    data class LocationInfo(
        val district: String?,
        val city: String
    )

    data class WeatherWithLocation(
        val location: LocationInfo,
        val weather: WeatherData
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
    // Lấy tên địa điểm từ tọa độ GPS

    suspend fun getLocationName(latitude: Double, longitude: Double): LocationInfo?{
        return withContext(Dispatchers.IO){
            try{
                //Tạo URL
                val urlString = buildString{
                    append(GEOCODING_BASE_URL)
                    append("?latlng=$latitude,$longitude")
                    append("&key=$GOOGLE_API_KEY")
                    append("&language=vi")
                }
                Log.d("GEOCODING_API", "Calling: $urlString")

                //Goi API
                val client = OkHttpClient()
                val request = Request.Builder().url(urlString).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    Log.e("GEOCODING_API", "API Error: ${response.code}")
                    return@withContext null
                }

                // 3. Parse response
                val jsonString = response.body?.string() ?: return@withContext null
                return@withContext parseGeocodeResponse(jsonString)

                }catch(e: Exception){
                Log.e("GEOCODING_API", "Exception: ${e.message}")
                return@withContext null
            }
        }
    }

    //Parse GeoCoding Response
    private fun parseGeocodeResponse(json: String): LocationInfo? {
        try{
            val jsonObj = JSONObject(json)
            val status = jsonObj.getString("status")
            if (status != "OK") {
                Log.e("GEOCODING_API", "Error status: $status")
                return null
            }

            val results = jsonObj.getJSONArray("results")
            if (results.length() == 0) {
                return null
            }
            // Lấy kết quả đầu tiên (chính xác nhất)
            val firstResult = results.getJSONObject(0)
            val formattedAddress = firstResult.getString("formatted_address")
            // Parse address components
            val addressComponents = firstResult.getJSONArray("address_components")
            var city: String? = null
            var district: String? = null
            for(i in 0 until addressComponents.length()){
                val component = addressComponents.getJSONObject(i)
                val types = component.getJSONArray("types")
                val longName = component.getString("long_name")
                for(j in 0 until types.length()){
                    val type = types.getString(j)
                    when(type){
                        "administrative_area_level_1" -> city = longName
                        "administrative_area_level_2" -> district = longName
                    }
                }
            }
            return LocationInfo(
                district = district,
                city = city ?: "Không xác định"
            )
        }catch(e: Exception){
            Log.e("GEOCODING_API", "Parse error: ${e.message}")
            return null
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


























