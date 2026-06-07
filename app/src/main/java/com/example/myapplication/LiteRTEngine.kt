package com.example.myapplication // Sửa lại đúng package của bạn

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LiteRTEngine(private val context: Context) : ILocalAIEngine {
    private var engine: Engine? = null

    override suspend fun initialize(modelPath: String) = withContext(Dispatchers.IO) {
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            Log.e("LocalAI", "Lỗi: Không tìm thấy file mô hình tại ${modelFile.absolutePath}")
            return@withContext
        }

        try {
            // Chạy trên CPU để đảm bảo ổn định nhiệt độ cho điện thoại khi cắm vào xe
            engine = Engine(
                EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.CPU(),
                    cacheDir = context.cacheDir.absolutePath
                )
            ).apply { initialize() }
            Log.i("LocalAI", "Lõi AI Offline (LiteRT) đã sẵn sàng!")
        } catch (e: Exception) {
            Log.e("LocalAI", "Khởi tạo thất bại: ${e.message}")
        }
    }

    override suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        val currentEngine = engine ?: return@withContext "Hệ thống ngoại tuyến chưa sẵn sàng."
        var responseText = ""

        try {
            // Cấu hình nhân cách AI cho ô tô
            val conversation = currentEngine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of("Bạn là trợ lý ảo trên ô tô. Trả lời ngắn gọn, trực tiếp.")
                )
            )

            // Nhận luồng câu trả lời
            conversation.sendMessageAsync(prompt).collect { message ->
                responseText += message.contents.toString()
            }
            conversation.close()

            // Xóa rác văn bản (Prefix "Assistant:") nếu mô hình bị ảo giác
            return@withContext responseText
                .replace(Regex("^\\s*Assistant\\s*:\\s*", RegexOption.IGNORE_CASE), "")
                .trim()

        } catch (e: Exception) {
            return@withContext "Lỗi xử lý ngoại tuyến: ${e.message}"
        }
    }

    override fun close() {
        if (engine?.isInitialized() == true) {
            engine?.close()
        }
    }
}