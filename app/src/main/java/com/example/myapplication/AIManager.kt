package com.example.myapplication

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

class AIManager(private val context: Context) {
    private val factory = AIEngineFactory(context)
    private var localEngine: ILocalAIEngine? = null
    private val downloadManager = ModelDownloadManager(context)

    // Đặt mặc định trạng thái để báo cho UI
    private var systemInitError: String? = "Hệ thống đang khởi động..."

    suspend fun initialize() {
        val activeModel = downloadManager.getActiveModel()
        if (activeModel != null) {
            localEngine = factory.createEngine(activeModel)

            // File nằm ở thẻ nhớ ngoài (Nơi vừa dùng ADB push vào)
            val externalFile = java.io.File(context.getExternalFilesDir(null), activeModel.fileName)
            // File nằm ở bộ nhớ trong (Nơi AI có quyền mmap file > 2GB)
            val internalFile = java.io.File(context.filesDir, activeModel.fileName)

            // BẢN VÁ LỖI MMAP 2GB: Copy file vào bộ nhớ trong nếu chưa có
            if (externalFile.exists() && (!internalFile.exists() || internalFile.length() != externalFile.length())) {
                systemInitError = "Đang tối ưu hóa mô hình 3.2GB vào bộ nhớ lõi. Vui lòng đợi 1 - 2 phút và thử chat lại..."
                Log.i("LocalAI", "Bắt đầu copy file vào bộ nhớ trong (Internal Storage)...")

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        externalFile.inputStream().use { input ->
                            internalFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        Log.i("LocalAI", "Copy hoàn tất! Sẵn sàng nạp mô hình.")
                    } catch (e: Exception) {
                        systemInitError = "Lỗi khi copy file vào bộ nhớ lõi: ${e.message}"
                        return@withContext
                    }
                }
            }

            // Nạp file từ bộ nhớ trong
            val finalPath = if (internalFile.exists()) internalFile.absolutePath else externalFile.absolutePath
            systemInitError = localEngine?.initialize(finalPath)
        } else {
            systemInitError = "Lỗi: Không lấy được Active Model từ ModelDownloadManager!"
            Log.e("AIManager", systemInitError!!)
        }
    }

    suspend fun getResponse(prompt: String, useLocalMode: Boolean): String {
        return if (useLocalMode) {
            if (systemInitError != null) {
                return "[Local] $systemInitError"
            }
            val response = localEngine?.generateResponse(prompt) ?: "Lỗi chưa rõ nguyên nhân."
            "[Local] $response"
        } else {
            var response = ""
            GeminiManager.chatWithAIStream(prompt).collect { chunk -> response += chunk }
            "[Cloud] $response"
        }
    }

    suspend fun getResponseStream(prompt: String, useLocalMode: Boolean): Flow<String> {
        return if (useLocalMode) {
            flow {
                if (systemInitError != null) {
                    emit("[Local] $systemInitError")
                } else {
                    val response = localEngine?.generateResponse(prompt) ?: "Lỗi chưa rõ nguyên nhân."
                    emit("[Local] $response")
                }
            }
        } else {
            GeminiManager.chatWithAIStream(prompt)
        }
    }

    fun close() {
        localEngine?.close()
    }
}