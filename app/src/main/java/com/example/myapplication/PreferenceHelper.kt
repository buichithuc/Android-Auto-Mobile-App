// Đặt file này ở đâu đó trong dự án (ví dụ: PreferenceHelper.kt)
package com.example.myapplication

import android.content.Context

object PreferenceHelper {
    private const val PREFS_NAME = "AiCarPrefs"
    private const val KEY_USE_LOCAL_LLM = "use_local_llm"

    fun setLocalMode(context: Context, isEnabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_USE_LOCAL_LLM, isEnabled).apply()
    }

    fun isLocalModeEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_USE_LOCAL_LLM, false)
    }
}