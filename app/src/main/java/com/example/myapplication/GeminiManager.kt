package com.example.myapplication

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.myapplication.BuildConfig
import com.google.ai.client.generativeai.type.Schema
import com.google.ai.client.generativeai.type.Tool
import com.google.ai.client.generativeai.type.defineFunction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.google.ai.client.generativeai.type.FunctionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


object GeminiManager {
    private const val API_KEY = BuildConfig.GEMINI_API_KEY // Lấy key từ Google AI Studio

    private const val TIER_1_MODEL = "gemini-3.1-flash-lite"
    private const val TIER_2_MODEL = "gemini-2.5-flash-lite"
    private const val TIER_3_MODEL = "gemini-3.5-flash"

    private fun getCurrentDateTime(): String {
        val sdf = SimpleDateFormat("EEEE, dd/MM/yyyy HH:mm", Locale("vi", "VN"))
        return sdf.format(Date())
    }

    // Khai báo hộp công cụ (Dùng listOf chứa Schema)
    private val webSearchTool = Tool(
        functionDeclarations = listOf(
            defineFunction(
                name = "search_web",
                description = "Sử dụng công cụ này BẮT BUỘC khi cần thông tin realtime, tỷ số thể thao, tin tức, thời tiết.",
                parameters = listOf(
                    Schema(
                        name = "query",
                        // SỬA LẠI ĐOẠN MÔ TẢ NÀY ĐỂ ÉP AI TẠO TỪ KHÓA CHUẨN:
                        description = "Từ khóa tìm kiếm Google ngắn gọn, tối ưu. TUYỆT ĐỐI KHÔNG đưa ngày/tháng/năm cụ thể vào từ khóa trừ khi bắt buộc. Chỉ dùng danh từ chính. VD: thay vì 'tỷ số world cup ngày 14/6/2026', hãy dùng 'kết quả World Cup 2026 mới nhất'.",
                        type = FunctionType.STRING
                    )
                )
            )
        )
    )


    private val sharedConfig = generationConfig {
        temperature = 0.7f
        maxOutputTokens = 2000
    }

    private val sharedSystemPrompt = content {
        text("Bạn là trợ lý lái xe. " +
                "THÔNG TIN QUAN TRỌNG: Hôm nay là ${getCurrentDateTime()}." +
                "QUAN TRỌNG: Không sử dụng Markdown formatting (**bold**, *italic*, # heading, `code`, [link](url), v.v.). " +
                "Sử dụng văn bản thuần túy, rõ ràng ")
    }


    private val tier1Model = GenerativeModel(modelName = TIER_1_MODEL, apiKey = API_KEY, generationConfig = sharedConfig, systemInstruction = sharedSystemPrompt, tools = listOf(webSearchTool))
    private val tier2Model = GenerativeModel(modelName = TIER_2_MODEL, apiKey = API_KEY, generationConfig = sharedConfig, systemInstruction = sharedSystemPrompt)
    private val tier3Model = GenerativeModel(modelName = TIER_3_MODEL, apiKey = API_KEY, generationConfig = sharedConfig, systemInstruction = sharedSystemPrompt)

    private var chatSession = tier1Model.startChat(history = emptyList())

    suspend fun chatWithAIStream(userInput: String): Flow<String> = flow {
        if (userInput.isBlank()) {
            emit("Tôi chưa nghe thấy câu hỏi")
            return@flow
        }

            try {
                val responseStream = chatSession.sendMessageStream(userInput)
                var hasFunctionCall = false
                var functionName = ""
                var searchQuery = ""

                responseStream.collect{ chunk ->


                    val call = chunk.functionCall

                    // Nếu call khác null nghĩa là có yêu cầu gọi hàm
                    if (call != null) {
                        hasFunctionCall = true
                        functionName = call.name
                        // Rút trích từ khóa tìm kiếm mà AI đã tự suy luận ra
                        searchQuery = call.args["query"] as? String ?: ""
                    }

                    // In văn bản bình thường ra màn hình (nếu có)
                    chunk.text?.let { emit(it) }
                }
                if(hasFunctionCall && functionName == "search_web"){
                    emit("\nĐang tìm kiếm dữ liệu mới nhất: '$searchQuery'\n")

                    val rawSearchResults = SearchManager.searchGoogle(searchQuery)

                    Log.d("GEMINI_DEBUG", "Dữ liệu cào từ Google: $rawSearchResults")
                    // BƠM DỮ LIỆU TÌM KIẾM NGƯỢC LẠI CHO AI (RAG)
                    val finalResponseStream = chatSession.sendMessageStream(
                        content(role = "function") {
                            part(com.google.ai.client.generativeai.type.FunctionResponsePart(
                                "search_web",
                                org.json.JSONObject(mapOf("results" to rawSearchResults))
                            ))
                        }
                    )

                    // AI đọc kết quả Search và trả lời người dùng
                    finalResponseStream.collect { chunk ->
                        chunk.text?.let { emit(it) }
                    }
                }
            } catch (e: Exception) {
                Log.e("GEMINI_DEBUG", "Lỗi chi tiết: ${e.message}")
                e.printStackTrace()

                val errorMsg = e.message ?: ""
                // Bắt các lỗi do nội dung (An toàn, Max Tokens) -> Không cần đổi server
                if (errorMsg.contains("SAFETY") || errorMsg.contains("MAX_TOKENS")) {
                    emit("Vì lý do an toàn hoặc dữ liệu quá dài, tôi không thể xử lý câu này.")
                    return@flow
                }

                Log.w("GEMINI_DEBUG", "Tier 1 ($TIER_1_MODEL) nghẽn! Đẩy luồng sang Tier 2 ($TIER_2_MODEL) để giữ tốc độ...")

                try{
                    val sessionTier2 = tier2Model.startChat(history = chatSession.history)
                    val responseStreamTier2 = sessionTier2.sendMessageStream(userInput)
                    responseStreamTier2.collect { chunk ->
                        chunk.text?.let { emit(it) }
                    }
                    chatSession = tier1Model.startChat(history = sessionTier2.history)


                }catch(e2 : Exception){
                    Log.w("GEMINI_DEBUG", "Cụm Lite sập toàn tập! Đánh thức Trùm cuối Tier 3 ($TIER_3_MODEL) gánh hệ thống...")
                    try{
                        val sessionTier3 = tier3Model.startChat(history = chatSession.history)
                        val responseStreamTier3 = sessionTier3.sendMessageStream(userInput)

                        responseStreamTier3.collect { chunk ->
                            chunk.text?.let { emit(it) }
                        }
                        chatSession = tier1Model.startChat(history = sessionTier3.history)
                    }catch(e3: Exception){
                        emit("Hiện tại toàn bộ hệ thống AI đang quá tải. Xin bạn vui lòng thử lại sau ít phút nhé!")
                    }
                }

            }
        }

    fun clearChatHistory() {
        chatSession = tier1Model.startChat(history = emptyList())
    }

    suspend fun resumeChatSession(history: List<ChatMessage>) {
        // Chuyển đổi toàn bộ mảng ChatMessage từ Firestore thành định dạng Content mà Gemini SDK yêu cầu
        val geminiHistory = history.map {msg ->
            content(role = if(msg.isUser) "user" else "model"){
                text(msg.text)
            }
        }
        // Khởi tạo đè một phiên chat mới nhưng có nạp sẵn toàn bộ lịch sử hội thoại trước đó
        chatSession = tier1Model.startChat(history = geminiHistory)
        Log.d("GEMINI_DEBUG", "Đã nạp thành công ${history.size} tin nhắn vào ngữ cảnh mới.")
    }
}












