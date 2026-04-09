package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.Locale

// SESSION
class MyAiSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return MyAiScreen(carContext)
    }
}

// SCREEN (GIAO DIỆN & LOGIC)
class MyAiScreen(carContext: CarContext) : Screen(carContext), TextToSpeech.OnInitListener {
    private var displayMessage = "Nhấn nút để bắt đầu nói"
    private var tts: TextToSpeech? = null

    init {
        // Khởi tạo TTS an toàn
        tts = TextToSpeech(carContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("vi", "VN"))
            if(result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Ngôn ngữ tiếng Việt chưa được hỗ trợ hoặc chưa tải về")
            } else{
                Log.d("TTS", "Khởi tạo TTS tiếng Việt thành công!")
            }
        }else {
            Log.e("TTS", "Khởi tạo thất bại, mã lỗi: $status")
        }
    }

    override fun onGetTemplate(): Template {
        val action = Action.Builder()
            .setTitle("Hỏi AI Agent")
            .setOnClickListener {
                displayMessage = "Đang lắng nghe..."
                invalidate()
                startListening()
            }
            .build()

        return MessageTemplate.Builder(displayMessage)
            .setHeaderAction(Action.APP_ICON)
            .setTitle("Trợ lý AI Agent")
            .addAction(action)
            .build()
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(carContext)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val textInput = data?.get(0) ?: ""
                processWithAI(textInput)
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                displayMessage = "Lỗi nhận diện. Hãy thử lại."
                invalidate()
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer.startListening(intent)
    }

    private fun processWithAI(input: String) {
        lifecycleScope.launch {
            displayMessage = "Đang xử lý..."
            invalidate()

            val aiResponse = GroqManager.chatWithAI(input)
            displayMessage = aiResponse
            invalidate()

            val audioManager = carContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            val result = audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                tts?.speak(aiResponse, TextToSpeech.QUEUE_FLUSH, null, "GroqTTS")
            }
        }
    }
}