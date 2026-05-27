package com.example.myapplication

import com.google.firebase.firestore.PropertyName

// 1. Thêm từ khóa 'data' để mở khóa hàm .copy()
// 2. Gán sẵn giá trị mặc định (= "", = true, = 0L) để Firebase tự động hiểu đây là constructor rỗng
data class ChatMessage(
    val text: String = "",
    
    @field:JvmField
    var isUser: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

object ChatViewType {
    const val USER = 1
    const val AI = 2
}