package com.example.myapplication

class ChatMessage {
    var text: String = ""
    var isUser: Boolean = true
    var timestamp: Long = System.currentTimeMillis()

    // Constructor trống bắt buộc phải có để Firebase không bị crash deserialize
    constructor()

    constructor(text: String, isUser: Boolean, timestamp: Long = System.currentTimeMillis()) {
        this.text = text
        this.isUser = isUser
        this.timestamp = timestamp
    }
}

object ChatViewType{
    const val USER = 1
    const val AI = 2
}