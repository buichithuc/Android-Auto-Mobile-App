package com.example.myapplication

import android.content.Context

class AIEngineFactory(private val context: Context) {
    
    fun createEngine(model: AiModel): ILocalAIEngine {
        return when (model.backendType) {
            BackendType.LITERT -> LiteRTEngine(context)
            BackendType.ONNX -> OnnxEngine(context) // Dùng ONNX nếu được chọn
        }
    }
}
