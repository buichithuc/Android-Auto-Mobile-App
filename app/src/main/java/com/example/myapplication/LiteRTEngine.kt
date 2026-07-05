package com.example.myapplication

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

    override suspend fun initialize(modelPath: String): String? = withContext(Dispatchers.IO) {
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            return@withContext "Không tìm thấy file tại đường dẫn: ${modelFile.absolutePath}"
        }

        try {
            engine = Engine(
                EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.CPU(),
                    cacheDir = context.filesDir.absolutePath
                )
            ).apply { initialize() }
            Log.i("LocalAI", "Lõi AI Offline (LiteRT) đã sẵn sàng!")
            return@withContext null // Thành công, không có lỗi
        } catch (e: Exception) {
            Log.e("LocalAI", "Khởi tạo thất bại: ${e.message}")
            return@withContext "Lỗi nạp mô hình LiteRT: ${e.localizedMessage}"
        }
    }

    override suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        val currentEngine = engine ?: return@withContext "Lõi Engine đang bị cấu hình lỗi hoặc chưa nạp."
        var responseText = ""

        try {
            val conversation = currentEngine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of("Bạn là trợ lý ảo trên ô tô" +  "QUAN TRỌNG: Không sử dụng Markdown formatting (**bold**, *italic*, # heading, `code`, [link](url), v.v.). " +
                            "Sử dụng văn bản thuần túy, rõ ràng. ")
                )
            )

            conversation.sendMessageAsync(prompt).collect { message ->
                responseText += message.contents.toString()
            }
            conversation.close()

            return@withContext responseText
                .replace(Regex("^\\s*Assistant\\s*:\\s*", RegexOption.IGNORE_CASE), "")
                .trim()

        } catch (e: Exception) {
            return@withContext "Lỗi xử lý sinh văn bản: ${e.message}"
        }
    }

    override fun close() {
        if (engine?.isInitialized() == true) {
            engine?.close()
        }
    }
}