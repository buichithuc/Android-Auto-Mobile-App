package com.example.myapplication

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

class ModelDownloadManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("AiCarPrefs", Context.MODE_PRIVATE)

    // ĐỒNG BỘ: Gom tất cả về thư mục Download ẩn của ứng dụng
    fun getModelDirectory(): File {
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(publicDownloads, "AiModels")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun downloadModel(model: AiModel): Long {
        val destinationName = if (model.isZip) "${model.fileName}.zip" else model.fileName

        val request = DownloadManager.Request(Uri.parse(model.downloadUrl))
            .setTitle("Đang tải ${model.name}")
            .setDescription("Vui lòng không tắt mạng...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AiModels/$destinationName")

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)

        prefs.edit().putString("download_id_$downloadId", model.id).apply()
        return downloadId
    }

    fun deleteModel(model: AiModel): Boolean {
        val fileOrDir = File(getModelDirectory(), model.fileName)
        return if (fileOrDir.exists()) {
            if (fileOrDir.isDirectory) fileOrDir.deleteRecursively() else fileOrDir.delete()

            if (getActiveModelId() == model.id) {
                setActiveModelId("gemma_chuan_litert")
            }
            true
        } else false
    }

    fun isModelDownloaded(model: AiModel): Boolean {
        val fileOrDir = File(getModelDirectory(), model.fileName)
        return if (model.isZip) {
            fileOrDir.exists() && fileOrDir.isDirectory && (fileOrDir.listFiles()?.isNotEmpty() == true)
        } else {
            fileOrDir.exists()
        }
    }

    fun setActiveModelId(modelId: String) {
        prefs.edit().putString("active_model_id", modelId).apply()
    }

    fun getActiveModelId(): String? {
        return prefs.getString("active_model_id", "gemma_chuan_litert")
    }

    fun getActiveModel(): AiModel? {
        val activeId = getActiveModelId()
        return ModelRegistry.supportedModels.find { it.id == activeId }
    }

    fun unzip(zipFilePath: File, destDirectory: File) {
        if (!destDirectory.exists()) destDirectory.mkdirs()
        ZipFile(zipFilePath).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val destFile = File(destDirectory, entry.name)
                if (entry.isDirectory) {
                    destFile.mkdirs()
                } else {
                    destFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }
}