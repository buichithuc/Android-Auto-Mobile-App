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
    val isZip: Boolean = false // Cờ đánh dấu file nén
)

object ModelRegistry {
    val supportedModels = listOf(
        AiModel(
            id = "gemma_chuan_litert",
            name = "Gemma 2B (LiteRT)",
            description = "Mô hình định dạng chuẩn cho LiteRT, nhẹ, phù hợp RAM 4GB.",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true",
            fileName = "gemma-4-E2B-it.litertlm",
            backendType = BackendType.LITERT,
            isZip = false
        ),
        AiModel(
            id = "qwen_2.5_0.5b_onnx",
            name = "Qwen 2.5 0.5B (ONNX)",
            description = "Phiên bản siêu nhẹ, phản hồi cực nhanh. Yêu cầu RAM 2GB.",
            downloadUrl = "https://huggingface.co/thucbc/Qwen-2.5-0.5B-ONNX-Android/resolve/main/qwen_0.5b_onnx_folder.zip?download=true",
            fileName = "qwen_0.5b_onnx_folder",
            backendType = BackendType.ONNX,
            isZip = true
        ),
    )
}