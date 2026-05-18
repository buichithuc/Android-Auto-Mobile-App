package com.example.myapplication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel : ViewModel() {
    // 1. Khởi tạo các đối tượng kết nối Firebase
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping

    // 2. Tạo ID ngẫu nhiên cho phiên chat hiện tại trên điện thoại
    private var currentSessionId: String = UUID.randomUUID().toString()
    private var isNewSession = true

    fun sendMessage(content: String) {
        val textTrimmed = content.trim()
        if (textTrimmed.isNotEmpty()) {
            // Kiểm tra an toàn tài khoản người dùng
            val uid = auth.currentUser?.uid ?: return
            val currentTime = System.currentTimeMillis()

            // BƯỚC A: Nếu là câu hỏi đầu tiên của phiên, tạo ngay Document Session trên Cloud
            if (isNewSession) {
                val sessionTitle = if (textTrimmed.length > 25) textTrimmed.take(25) + "..." else textTrimmed
                val sessionData = hashMapOf(
                    "title" to sessionTitle,
                    "timestamp" to currentTime
                )
                db.collection("users").document(uid)
                    .collection("sessions").document(currentSessionId)
                    .set(sessionData)

                isNewSession = false
            }

            // BƯỚC B: Lưu tin nhắn của Người dùng lên UI và Cloud Firestore
            val userMsg = ChatMessage(textTrimmed, true, currentTime)
            _messages.value = _messages.value + userMsg

            db.collection("users").document(uid)
                .collection("sessions").document(currentSessionId)
                .collection("messages").add(userMsg)

            // Bật hiệu ứng AI đang gõ
            _isTyping.value = true

            // BƯỚC C: Gọi Gemini AI xử lý bất đồng bộ
            viewModelScope.launch {
                try {
                    val response = GeminiManager.chatWithAI(textTrimmed)
                    val aiTime = System.currentTimeMillis()
                    val aiMsg = ChatMessage(response, false, aiTime)

                    // Cập nhật UI cục bộ hiển thị tin nhắn AI
                    _messages.value = _messages.value + aiMsg

                    // Lưu tin nhắn của AI trả về lên Cloud Firestore
                    db.collection("users").document(uid)
                        .collection("sessions").document(currentSessionId)
                        .collection("messages").add(aiMsg)

                    // Cập nhật lại thời gian của Session để ô tô luôn quét được cuộc chat này lên đầu danh sách
                    db.collection("users").document(uid)
                        .collection("sessions").document(currentSessionId)
                        .update("timestamp", aiTime)

                } catch (e: Exception) {
                    _messages.value = _messages.value + ChatMessage("Lỗi kết nối AI.", false, System.currentTimeMillis())
                } finally {
                    // Tắt hiệu ứng AI đang gõ
                    _isTyping.value = false
                }
            }
        }
    }
}