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
        // Đây là mô hình chính chủ chuẩn định dạng LITERTLM mới nhất
        AiModel(
            id = "gemma_chuan_litert",
            name = "Gemma 2B (Chuẩn LiteRT)",
            description = "Mô hình định dạng chuẩn cho LiteRT 0.12.0",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm",
            fileName = "gemma-4-E2B-it.litertlm", // Đổi tên file khớp 100% với file tải về
            backendType = BackendType.LITERT
        ),
        AiModel("qwen_2.5_onnx", "Qwen 2.5 (ONNX)", "Độ chính xác cao", "https://example.com/qwen2.5", "qwen2.5.onnx", BackendType.ONNX)
    )
}