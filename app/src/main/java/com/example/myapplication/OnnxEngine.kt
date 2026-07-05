package com.example.myapplication

import android.content.Context
import android.util.Log
import ai.onnxruntime.genai.Model
import ai.onnxruntime.genai.Tokenizer
import ai.onnxruntime.genai.GeneratorParams
import ai.onnxruntime.genai.Generator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class OnnxEngine(private val context: Context) : ILocalAIEngine {

    private var model: Model? = null
    private var tokenizer: Tokenizer? = null

    override suspend fun initialize(modelPath: String): String? = withContext(Dispatchers.IO) {
        val modelDir = File(modelPath)

        // ONNX GenAI yêu cầu modelPath phải là một THƯ MỤC chứa đủ file
        if (!modelDir.exists() || !modelDir.isDirectory) {
            return@withContext "Lỗi: Đường dẫn ONNX phải là một thư mục hợp lệ tại: ${modelDir.absolutePath}"
        }

        try {
            Log.i("LocalAI_ONNX", "Đang nạp mô hình ONNX từ thư mục: ${modelDir.absolutePath}")
            model = Model(modelDir.absolutePath)
            tokenizer = Tokenizer(model)
            Log.i("LocalAI_ONNX", "Lõi AI Offline (ONNX) đã sẵn sàng!")
            return@withContext null // Khởi tạo thành công
        } catch (e: Exception) {
            Log.e("LocalAI_ONNX", "Lỗi nạp mô hình ONNX: ${e.message}")
            return@withContext "Lỗi ONNX: ${e.localizedMessage}"
        }
    }

    override suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        val currentModel = model ?: return@withContext "ONNX Engine chưa được nạp mô hình."
        val currentTokenizer = tokenizer ?: return@withContext "ONNX Tokenizer chưa được nạp."

        try {
            // Bổ sung System Prompt ép mô hình nhận vai trò và dùng tiếng Việt
            val systemInstruction = "Bạn là trợ lý AI thông minh. Hãy trả lời các câu hỏi một cách ngắn gọn, chính xác và tự nhiên bằng tiếng Việt."

            // Đóng gói theo chuẩn ChatML hoàn chỉnh của Qwen 2.5
            val formattedPrompt = "<|im_start|>system\n$systemInstruction<|im_end|>\n<|im_start|>user\n$prompt<|im_end|>\n<|im_start|>assistant\n"

            // Chuyển text thành Token ID (Trả về đối tượng Sequences)
            val inputTokens = currentTokenizer.encode(formattedPrompt)

            // Cấu hình tham số sinh văn bản
            val params = GeneratorParams(currentModel)
            params.setSearchOption("max_length", 1024.0)

            // Khởi tạo Generator
            val generator = Generator(currentModel, params)

            // [API MỚI] Nạp prompt vào generator thay vì params
            // Nếu Android Studio báo đỏ hàm này, hãy thử gõ "generator.appendTokens(inputTokens)"
            generator.appendTokenSequences(inputTokens)

            val responseBuilder = java.lang.StringBuilder()

            // [TỐI ƯU] Sử dụng TokenizerStream để stream chữ mượt mà, không lỗi dấu câu
            val tokenizerStream = currentTokenizer.createStream()

            // [API MỚI] Vòng lặp sinh từng Token một
            while (!generator.isDone) {
                // Tự động tính toán Logits và sinh token mới
                generator.generateNextToken()

                // Lấy mảng Token ID của luồng số 0, sau đó lấy ID cuối cùng
                val currentSequence = generator.getSequence(0)
                val nextTokenId = currentSequence[currentSequence.size - 1]

                // Giải mã Token ID vừa sinh ra và ghép vào chuỗi kết quả
                val tokenStr = tokenizerStream.decode(nextTokenId)
                responseBuilder.append(tokenStr)
            }

            // Dọn dẹp bộ nhớ RAM (Rất quan trọng trên Android để tránh tràn RAM)
            tokenizerStream.close()
            generator.close()
            params.close()
            inputTokens.close()

            return@withContext responseBuilder.toString().trim()

        } catch (e: Exception) {
            Log.e("LocalAI_ONNX", "Lỗi sinh văn bản: ${e.message}")
            return@withContext "Lỗi xử lý ONNX: ${e.message}"
        }
    }

    override fun close() {
        tokenizer?.close()
        model?.close()
        tokenizer = null
        model = null
        Log.i("LocalAI_ONNX", "Đã giải phóng tài nguyên ONNX")
    }
}