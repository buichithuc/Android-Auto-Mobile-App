package com.example.myapplication

import android.content.Context
import android.os.Environment
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

            // Đoạn code ĐÚNG:
            val publicDownloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val aiModelsDir = java.io.File(publicDownloads, "AiModels")
            val externalFile = java.io.File(aiModelsDir, activeModel.fileName)

            // Cập nhật lại đường dẫn finalPath để trỏ vào thư mục AiModels
            var finalPath = externalFile.absolutePath

            // Phần logic kiểm tra và copy sang internalFile vẫn giữ nguyên...
            // Đảm bảo internalFile cũng trỏ đúng tên file
            val internalFile = java.io.File(context.filesDir, activeModel.fileName)

            if (activeModel.backendType == BackendType.LITERT) {
                //  Kiểm tra cả sự tồn tại VÀ dung lượng file. Nếu lệch dung lượng -> Copy đè!
                if (externalFile.exists()) {
                    if (!internalFile.exists() || internalFile.length() != externalFile.length()) {
                        systemInitError = "Đang nạp file 2GB vào bộ nhớ lõi (Vui lòng đợi)..."
                        Log.i("LocalAI", "Bắt đầu copy đè file chuẩn vào bộ nhớ trong...")
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                externalFile.inputStream().use { input ->
                                    internalFile.outputStream().use { output -> input.copyTo(output) }
                                }
                            } catch (e: Exception) {
                                systemInitError = "Lỗi copy file: ${e.message}"
                            }
                        }
                    }
                }
                finalPath = internalFile.absolutePath
            } else {
                // ĐỐI VỚI ONNX: ONNX Runtime GenAI đọc trực tiếp từ thư mục ngoài rất tốt, không cần copy
                Log.i("LocalAI", "Chế độ ONNX: Sử dụng trực tiếp thư mục từ External Storage")
                if (!externalFile.exists() || !externalFile.isDirectory) {
                    systemInitError =
                        "Không tìm thấy thư mục cấu hình ONNX tại: ${externalFile.absolutePath}"
                    return
                }
            }

            if (File(finalPath).exists()) {
                systemInitError = localEngine?.initialize(finalPath)
            } else {
                systemInitError = "Không tìm thấy file mô hình tại $finalPath"
            }
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
                        val response =
                            localEngine?.generateResponse(prompt) ?: "Lỗi chưa rõ nguyên nhân."
                        emit("[Local] $response")
                    }
                }
            } else {
                GeminiManager.chatWithAIStream(prompt)
            }
        }


        suspend fun reloadAI() {
        close() // Giải phóng engine cũ
        initialize() // Nạp lại engine với mô hình mới
    }

        fun close() {
            localEngine?.close()
        }
}