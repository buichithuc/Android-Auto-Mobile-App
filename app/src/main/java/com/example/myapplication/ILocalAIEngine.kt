package com.example.myapplication

import android.content.Context

interface ILocalAIEngine {
    suspend fun initialize(modelPath: String)
    suspend fun generateResponse(prompt: String): String
    fun close()
}
