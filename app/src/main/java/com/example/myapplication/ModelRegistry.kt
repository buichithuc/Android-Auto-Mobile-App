package com.example.myapplication

enum class BackendType {
    LITERT, ONNX
}

data class AiModel(
    val id: String,
    val name: String,
    val description: String,
    val downloadUrl: String,
    val fileName: String,
    val backendType: BackendType,
    var isDownloaded: Boolean = false
)

object ModelRegistry {
    val supportedModels = listOf(
        AiModel("qwen_3_litert", "Qwen 3 (LiteRT)", "Nhẹ, nhanh cho máy yếu", "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf", "qwen3-0.6b.litertlm", BackendType.LITERT),
        AiModel("gemma_4_litert", "Gemma 4 (LiteRT)", "Thông minh, cần máy mạnh", "https://example.com/gemma", "gemma4.litertlm", BackendType.LITERT),
        AiModel("qwen_2.5_onnx", "Qwen 2.5 (ONNX)", "Độ chính xác cao", "https://example.com/qwen2.5", "qwen2.5.onnx", BackendType.ONNX)
    )
}
