package com.example.myapplication

data class SessionMetadata(
    val id: String = "",
    val title: String = "Cuộc trò chuyện mới",
    val timestamp: Long = System.currentTimeMillis()
)