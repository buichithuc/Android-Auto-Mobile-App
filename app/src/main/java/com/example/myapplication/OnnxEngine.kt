package com.example.myapplication

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File

class OnnxEngine(private val context: Context) : ILocalAIEngine {

    private var ortEnvironment: OrtEnvironment? = null
    private var session: OrtSession? = null

    override suspend fun initialize(modelPath: String) = withContext(Dispatchers.IO) {
        Log.i("OnnxEngine", "Đang khởi tạo lõi ONNX với mô hình: $modelPath")
        
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            Log.e("OnnxEngine", "Lỗi: Không tìm thấy file mô hình tại $modelPath")
            return@withContext
        }

        try {
            ortEnvironment = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions()
            
            // Cấu hình tối ưu (có thể tùy chỉnh thêm tùy phần cứng)
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            options.setIntraOpNumThreads(4)

            session = ortEnvironment?.createSession(modelFile.absolutePath, options)
            Log.i("OnnxEngine", "ONNX Engine đã load thành công mô hình và khởi tạo Session!")
        } catch (e: Exception) {
            Log.e("OnnxEngine", "Khởi tạo lõi ONNX thất bại: ${e.message}")
        }
    }

    override suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        val currentSession = session ?: return@withContext "Lõi ONNX chưa sẵn sàng. (Mô hình chưa được tải hoặc bị lỗi)"

        Log.i("OnnxEngine", "Sinh phản hồi từ ONNX cho: $prompt")
        
        // ----------------------------------------------------------------------
        // [LƯU Ý]: Việc đưa string vào ONNX đòi hỏi chuỗi Tokenization (BPE)
        // và KV Cache. Repo gốc `local-llms-on-android` đã thiết kế hệ thống
        // BpeTokenizer, PromptBuilder, và TensorUtils cực kỳ chi tiết.
        // Tại đây chúng ta chỉ gọi giả lập kết quả trước, để tránh phình to file.
        // Bạn sẽ cần mang các class Tokenizer và OnnxModel từ repo đó sang.
        // ----------------------------------------------------------------------
        
        Thread.sleep(1000) // Giả lập độ trễ AI
        return@withContext "Đây là phản hồi từ mô hình ONNX với câu hỏi: $prompt. (Vui lòng cấy Tokenizer để mô hình thực sự suy luận)"
    }

    override fun close() {
        Log.i("OnnxEngine", "Đang dọn dẹp bộ nhớ của ONNX Engine")
        try {
            session?.close()
            ortEnvironment?.close()
        } catch (e: Exception) {
            Log.e("OnnxEngine", "Lỗi khi đóng ONNX Engine: ${e.message}")
        } finally {
            session = null
            ortEnvironment = null
        }
    }
}
