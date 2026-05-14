package com.example.myapplication

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

object ChatViewType{
    const val USER = 1
    const val AI = 2
}