package com.example.myapplication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.util.copy
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel : ViewModel() {
    // Khởi tạo các đối tượng kết nối Firebase
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    // Danh sách các phiên chat hiển thị lên thanh trượt Sidebar
    private val _sessions = MutableStateFlow<List<SessionMetadata>>(emptyList())
    val sessions: StateFlow<List<SessionMetadata>> = _sessions

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping

    // Tạo ID ngẫu nhiên cho phiên chat hiện tại trên điện thoại
    private var currentSessionId: String = UUID.randomUUID().toString()
    private var isNewSession = true

    init {
        loadAllSessions()
    }

    // Lắng nghe thời gian thực danh sách cuộc hội thoại của User
    private fun loadAllSessions() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("sessions")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { value, error ->
                if(error != null) return@addSnapshotListener
                val list = mutableListOf<SessionMetadata>()
                value?.documents?.forEach{ doc ->
                    val session = doc.toObject(SessionMetadata::class.java)?.copy(id = doc.id)
                    if(session != null) list.add(session)
                }
                _sessions.value = list
            }
    }

    // Chọn một cuộc hội thoại cũ -> Kéo tin nhắn về hiển thị lên màn hình
    fun selectSession(sessionId: String){
        val uid = auth.currentUser?.uid ?: return
        currentSessionId = sessionId
        isNewSession = false

        db.collection("users").document(uid)
            .collection("sessions").document(sessionId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { docs ->
                val parsedMessages = docs.documents.map { doc ->
                    val text = doc.getString("text") ?: ""
                    val timestamp = doc.getLong("timestamp") ?: 0L

                    // Bắt bằng được biến boolean.
                    val isUser = doc.getBoolean("isUser") ?: doc.getBoolean("user") ?: true

                    ChatMessage(text, isUser, timestamp)
                }

                _messages.value = parsedMessages

                viewModelScope.launch {
                    GeminiManager.resumeChatSession(parsedMessages) // Đồng bộ não bộ Gemini cục bộ
                }
            }

    }

    fun startNewChatSession(){
        currentSessionId = UUID.randomUUID().toString()
        isNewSession = true
        _messages.value = emptyList()
        GeminiManager.clearChatHistory()
    }

    fun deleteSession(sessionId: String){
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).collection("sessions").document(sessionId)
            .delete()
            .addOnSuccessListener {
                if (currentSessionId == sessionId) {
                    startNewChatSession()
                }
            }
    }

    fun sendMessage(content: String) {
        val textTrimmed = content.trim()
        if (textTrimmed.isNotEmpty()) {
            // Kiểm tra an toàn tài khoản người dùng
            val uid = auth.currentUser?.uid ?: return
            val currentTime = System.currentTimeMillis()

            // Nếu là câu hỏi đầu tiên của phiên, tạo ngay Document Session trên Cloud
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

            //  Lưu tin nhắn của Người dùng lên UI và Cloud Firestore
            val userMsg = ChatMessage(textTrimmed, true, currentTime)
            _messages.value = _messages.value + userMsg

            db.collection("users").document(uid)
                .collection("sessions").document(currentSessionId)
                .collection("messages").add(userMsg)

            // Bật hiệu ứng AI đang gõ
            _isTyping.value = true

            // Gọi Gemini AI xử lý bất đồng bộ
            viewModelScope.launch {
                try {
                    val aiTime = System.currentTimeMillis()
                    var fullResponse = ""
                    _messages.value = _messages.value + ChatMessage("", false, aiTime)


                    GeminiManager.chatWithAIStream(textTrimmed).collect{ chunk ->
                        fullResponse += chunk

                        val currentList = _messages.value.toMutableList()
                        val lastIndex = currentList.size - 1
                        currentList[lastIndex] = currentList[lastIndex].copy(text = fullResponse)
                        _messages.value = currentList


                    }

                    val finalAiMsg = ChatMessage(fullResponse, false, aiTime)

                    // Lưu tin nhắn của AI trả về lên Cloud Firestore
                    db.collection("users").document(uid)
                        .collection("sessions").document(currentSessionId)
                        .collection("messages").add(finalAiMsg)

                    // Cập nhật lại thời gian của Session để ô tô luôn quét được cuộc chat này lên đầu danh sách
                    db.collection("users").document(uid)
                        .collection("sessions").document(currentSessionId)
                        .update("timestamp", System.currentTimeMillis())

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