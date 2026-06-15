package com.example.myapplication

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object SearchManager {
    private val client = OkHttpClient()

    suspend fun searchGoogle(query: String): String = withContext(Dispatchers.IO) {
        try {
            val jsonPayload = JSONObject()
            jsonPayload.put("q", query)
            jsonPayload.put("num", 3)

            val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url("https://google.serper.dev/search")
                .addHeader("X-API-KEY", "9983299ffeeff58f79688094fa646ef43ec2d5b6")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val jsonObject = JSONObject(responseBody)
                val organic = jsonObject.optJSONArray("organic")

                if (organic == null || organic.length() == 0) return@withContext "Không tìm thấy thông tin trên web."

                val resultBuilder = StringBuilder()
                for (i in 0 until organic.length()) {
                    val item = organic.getJSONObject(i)
                    resultBuilder.append("- ${item.optString("title")}: ${item.optString("snippet")}\n")
                }
                return@withContext resultBuilder.toString()
            }
            return@withContext "Lỗi kết nối: ${response.code}"
        } catch (e: Exception) {
            return@withContext "Lỗi hệ thống: ${e.message}"
        }
    }
}