package com.example.myapplication

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationListScreen(carContext: CarContext) : Screen(carContext) {
    private var sessions = mutableListOf<SessionMetadata>()
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    init {
        fetchChatSessions()
    }

    private fun fetchChatSessions(){
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid)
            .collection("sessions")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(6)// Android Auto giới hạn số lượng item để đảm bảo an toàn
            .addSnapshotListener{ value, error ->
                if(error != null) return@addSnapshotListener

                sessions.clear()
                value?.documents?.forEach { doc ->
                    val session = doc.toObject(SessionMetadata::class.java)?.copy(id = doc.id)
                    if (session != null) sessions.add(session)
                }
                invalidate()

            }
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        listBuilder.addItem(
            Row.Builder()
                .setTitle("+ Cuộc trò chuyện mới")
                .addText("Bắt đầu một phiên thảo luận hoàn toàn mới với trợ lý AI")
                .setOnClickListener {
                    // Kích hoạt màn hình AI Screen và truyền vào 'null' để tạo luồng hội thoại mới tinh
                    screenManager.push(MyAiScreen(carContext, null))
                }
                .build()
        )

            sessions.forEach { session ->
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(session.title)
                        .addText("Lần cuối: ${formatTime(session.timestamp)}")
                        .setOnClickListener {
                            screenManager.push(MyAiScreen(carContext, session.id))
                        }
                        .build()
                )

            }

        return ListTemplate.Builder()
            .setLoading(false) // dùng SnapshotListener nên có thể quản lý loading tinh tế hơn
            .setTitle("AI Assistant - Chọn hội thoại")
            .setSingleList(listBuilder.build())
            .setHeaderAction(Action.APP_ICON)
            .build()

    }
    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

}