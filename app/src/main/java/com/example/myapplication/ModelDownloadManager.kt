package com.example.myapplication

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import java.io.File

class ModelDownloadManager(private val context: Context) {
    
    // TÍNH NĂNG 1: TẢI MÔ HÌNH
    fun downloadModel(model: AiModel): Long {
        val request = DownloadManager.Request(Uri.parse(model.downloadUrl))
            .setTitle("Đang tải ${model.name}")
            .setDescription("Vui lòng không tắt mạng...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, null, model.fileName)
            
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(request) // Trả về ID tiến trình tải
    }

    // TÍNH NĂNG 2: XÓA MÔ HÌNH
    fun deleteModel(model: AiModel): Boolean {
        val file = File(context.getExternalFilesDir(null), model.fileName)
        return if (file.exists()) {
            file.delete()
        } else false
    }

    // TÍNH NĂNG 3: KIỂM TRA ĐÃ TẢI CHƯA
    fun isModelDownloaded(model: AiModel): Boolean {
        val file = File(context.getExternalFilesDir(null), model.fileName)
        return file.exists()
    }
    
    // TÍNH NĂNG 4: CHUYỂN ĐỔI MÔ HÌNH (Lưu ID mô hình đang chọn)
    fun setActiveModelId(modelId: String) {
        val prefs = context.getSharedPreferences("AiCarPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("active_model_id", modelId).apply()
    }

    fun getActiveModelId(): String? {
        val prefs = context.getSharedPreferences("AiCarPrefs", Context.MODE_PRIVATE)
        return prefs.getString("active_model_id", "qwen_3_litert")
    }

    fun getActiveModel(): AiModel? {
        val activeId = getActiveModelId()
        return ModelRegistry.supportedModels.find { it.id == activeId }
    }
}
