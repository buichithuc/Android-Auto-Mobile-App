package com.example.myapplication

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AIManager(private val context: Context) {
    // Trạm điều phối đa lõi
    private val factory = AIEngineFactory(context)
    private var localEngine: ILocalAIEngine? = null
    
    // Hỗ trợ kiểm tra/tải mô hình
    private val downloadManager = ModelDownloadManager(context)

    suspend fun initialize() {
        val activeModel = downloadManager.getActiveModel()
        if (activeModel != null) {
            // Khởi tạo linh hoạt tùy thuộc BackendType
            localEngine = factory.createEngine(activeModel)
            
            // Lấy đường dẫn file trong máy
            val modelPath = java.io.File(context.getExternalFilesDir(null), activeModel.fileName).absolutePath
            localEngine?.initialize(modelPath)
        } else {
            Log.e("AIManager", "Chưa có mô hình nào được chọn hoặc mô hình không hợp lệ!")
        }
    }

    suspend fun getResponse(prompt: String, useLocalMode: Boolean): String {
        return if (useLocalMode) {
            Log.i("AIManager", "User chọn Local Mode -> Gọi Offline Assistant")
            val response = localEngine?.generateResponse(prompt) ?: "AI Offline chưa sẵn sàng. Vui lòng kiểm tra mô hình tải về."
            "[Local] $response"
        } else {
            Log.i("AIManager", "User chọn Cloud Mode -> Gọi Gemini")
            var response = ""
            GeminiManager.chatWithAIStream(prompt).collect { chunk ->
                 response += chunk
            }
            "[Cloud] $response"
        }
    }

    suspend fun getResponseStream(prompt: String, useLocalMode: Boolean): Flow<String> {
        return if (useLocalMode) {
            flow {
                Log.i("AIManager", "User chọn Local Mode -> Gọi Offline Assistant")
                val response = localEngine?.generateResponse(prompt) ?: "AI Offline chưa sẵn sàng. Vui lòng kiểm tra mô hình tải về."
                emit("[Local] $response")
            }
        } else {
            Log.i("AIManager", "User chọn Cloud Mode -> Gọi Gemini")
            GeminiManager.chatWithAIStream(prompt)
        }
    }
    fun close() {
        localEngine?.close()
    }

}