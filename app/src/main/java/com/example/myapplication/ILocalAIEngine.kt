package com.example.myapplication

interface ILocalAIEngine {
    suspend fun initialize(modelPath: String): String? // Trả về null nếu thành công, trả về chuỗi nếu có lỗi
    suspend fun generateResponse(prompt: String): String
    fun close()
}