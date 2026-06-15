package com.example.myapplication

import android.content.Context

class OnnxEngine(private val context: Context) : ILocalAIEngine {

    // Đã thêm : String? và return null để khớp với Interface mới
    override suspend fun initialize(modelPath: String): String? {
        // Tạm thời để trống vì chúng ta đang dùng LiteRT
        return null
    }

    override suspend fun generateResponse(prompt: String): String {
        return "ONNX Engine chưa được cài đặt hoàn chỉnh."
    }

    override fun close() {
        // Đóng tài nguyên nếu có
    }
}