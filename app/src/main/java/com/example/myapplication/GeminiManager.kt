package com.example.myapplication

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST


data class Message(val role: String, val content: String)
data class GroqRequest(
    val model: String,
    val messages: List<Message>,
    val max_tokens: Int = 150,
    val temperature: Double = 0.7
)

data class GroqResponse(val choices: List<Choice>)
data class Choice(val message: Message)

interface GroqApiService {
    @POST("chat/completions")
    suspend fun chatWithAI(
        @Header("Authorization") authHeader: String,
        @Body requestBody: GroqRequest
    ): GroqResponse
}

object GroqManager {
    private const val API_KEY = "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"
    private const val BASE_URL = "https://api.groq.com/openai/v1/"
    private const val MODEL_NAME = "llama-3.1-8b-instant"

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val service = retrofit.create(GroqApiService::class.java)

    suspend fun chatWithAI(userInput: String): String {
        if (userInput.isBlank()) {
            return "Tôi chưa nghe thấy câu hỏi"
        }

        return withContext(Dispatchers.IO) {
            try {
                val request = GroqRequest(
                    model = MODEL_NAME,
                    messages = listOf(
                        Message(
                            "system",
                            "Bạn là trợ lý lái xe, hãy trả lời ngắn gọn xúc tích dưới 30 từ"
                        ),
                        Message("user", userInput)
                    )
                )

                val response = service.chatWithAI("Bearer $API_KEY", request)
                response.choices.firstOrNull()?.message?.content ?: "AI không có phản hồi"
            } catch (e: Exception) {
                e.printStackTrace()

                // Categorize errors
                val msg = e.localizedMessage ?: ""
                when {
                    msg.contains("401") -> "Lỗi: API Key không hợp lệ"
                    msg.contains("429") -> "Lỗi: Hết hạn mức request (Rate limit)"
                    else -> "Lỗi kết nối: Không thể gọi AI."
                }
            }
        }
    }
}


