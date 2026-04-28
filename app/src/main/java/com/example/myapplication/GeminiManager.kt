package com.example.myapplication

//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//import retrofit2.Response
//import retrofit2.Retrofit
//import retrofit2.converter.gson.GsonConverterFactory
//import retrofit2.http.Body
//import retrofit2.http.Header
//import retrofit2.http.POST
//import com.example.myapplication.BuildConfig


//data class Message(val role: String, val content: String)
//data class GroqRequest(
//    val model: String,
//    val messages: List<Message>,
//    val max_tokens: Int = 150,
//    val temperature: Double = 0.7
//)
//
//data class GroqResponse(val choices: List<Choice>)
//data class Choice(val message: Message)
//
//interface GroqApiService {
//    @POST("chat/completions")
//    suspend fun chatWithAI(
//        @Header("Authorization") authHeader: String,
//        @Body requestBody: GroqRequest
//    ): GroqResponse
//}
//
//object GroqManager {
//    private val API_KEY = BuildConfig.GROQ_API_KEY
//    private const val BASE_URL = "https://api.groq.com/openai/v1/"
//    private const val MODEL_NAME = "llama-3.1-8b-instant"
//
//    private val retrofit = Retrofit.Builder()
//        .baseUrl(BASE_URL)
//        .addConverterFactory(GsonConverterFactory.create()) // biến json từ server thành đối tượng kotlin hiểu
//        .build()
//
//    private val service = retrofit.create(GroqApiService::class.java) //đối tượng thực tế có khả năng thực hiện các cuộc gọi mạng đến AI của Groq.
//
//    suspend fun chatWithAI(userInput: String): String {
//        if (userInput.isBlank()) {
//            return "Tôi chưa nghe thấy câu hỏi"
//        }
//
//        return withContext(Dispatchers.IO) {
//            try {
//                val request = GroqRequest(
//                    model = MODEL_NAME,
//                    messages = listOf(
//                        Message(
//                            "system",
//                            "Bạn là trợ lý lái xe. Nếu có nhiều ý, hãy trả lời bằng cách xuống dòng và dùng dấu gạch đầu dòng. Trả lời dưới 4 dòng."
//                        ),
//                        Message("user", userInput)
//                    )
//                )
//
//                val response = service.chatWithAI("Bearer $API_KEY", request)
//                response.choices.firstOrNull()?.message?.content ?: "AI không có phản hồi"
//            } catch (e: Exception) {
//                e.printStackTrace()
//
//                // Categorize errors
//                val msg = e.localizedMessage ?: ""
//                when {
//                    msg.contains("401") -> "Lỗi: API Key không hợp lệ"
//                    msg.contains("429") -> "Lỗi: Hết hạn mức request (Rate limit)"
//                    else -> "Lỗi kết nối: Không thể gọi AI."
//                }
//            }
//        }
//    }
//}


import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.myapplication.BuildConfig

object GeminiManager {
    private const val API_KEY = BuildConfig.GEMINI_API_KEY // Lấy key từ Google AI Studio
    private const val MODEL_NAME = "gemini-2.5-flash" // Bản Flash cực nhanh cho ô tô

    // Cấu hình mô hình
    private val model = GenerativeModel(
        modelName = MODEL_NAME,
        apiKey = API_KEY,
        generationConfig = generationConfig {
            temperature = 0.7f
            maxOutputTokens = 500
        },
        // System Prompt tương tự như bạn đã làm với Groq
        systemInstruction = content {
            text("Bạn là trợ lý lái xe. Nếu có nhiều ý, hãy trả lời bằng cách xuống dòng và dùng dấu gạch đầu dòng. Trả lời dưới 4 dòng.")
        }
    )

    suspend fun chatWithAI(userInput: String): String {
        if (userInput.isBlank()) return "Tôi chưa nghe thấy câu hỏi"

        return withContext(Dispatchers.IO) {
            try {
                val response = model.generateContent(userInput)
                response.text ?: "AI không có phản hồi" // Giá trị trả về nếu thành công
            } catch (e: Exception) {
                Log.e("GEMINI_DEBUG", "Lỗi chi tiết: ${e.message}")
                e.printStackTrace()

                val errorMsg = e.message ?: ""
                when {
                    // Xử lý riêng lỗi MAX_TOKENS để tài xế vẫn nhận được thông tin
                    errorMsg.contains("MAX_TOKENS") -> "Câu trả lời hơi dài, nhưng ý chính là: (Vui lòng thử lại với câu hỏi hẹp hơn)"
                    errorMsg.contains("SAFETY") -> "Vì lý do an toàn, tôi không thể trả lời câu hỏi này khi bạn đang lái xe."
                    else -> "Lỗi kết nối: AI đang bận một chút."
                }
            }
        }
    }
}


