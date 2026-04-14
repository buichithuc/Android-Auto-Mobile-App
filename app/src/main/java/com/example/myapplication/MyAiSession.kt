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
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.Locale


enum class AssistantState {
    IDLE,
    LISTENING,
    THINKING, // calling API Groq
    SPEAKING
}



// SESSION
class MyAiSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return MyAiScreen(carContext)
    }
}

// SCREEN (GIAO DIỆN & LOGIC)
class MyAiScreen(carContext: CarContext) : Screen(carContext), TextToSpeech.OnInitListener {
    private var currentState = AssistantState.IDLE
    private var displayMessage = "Chào bạn tôi có thể giúp gì cho bạn"
    private var tts: TextToSpeech? = null

    init {
        // Khởi tạo TTS an toàn
        tts = TextToSpeech(carContext, this)

        setupTtsListener()
    }

    // TỰ ĐỘNG RESET KHI AI NÓI XONG
    private fun setupTtsListener() {
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                // Khi AI nói xong, đưa nút bấm về lại hình Mic (IDLE)
                updateState(AssistantState.IDLE, displayMessage)
            }
            override fun onError(utteranceId: String?) {
                updateState(AssistantState.IDLE, "Lỗi phát âm thanh.")
            }
        })
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

        val iconRes = when (currentState) {
            AssistantState.LISTENING -> R.drawable.ic_mic
            AssistantState.SPEAKING -> R.drawable.ic_stop
            else -> R.drawable.ic_mic
        }

        val actionButton = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, iconRes))
                // Đổi màu nút sang đỏ khi đang nói để tài xế dễ nhận biết nút dừng
                .setTint(if (currentState == AssistantState.SPEAKING) CarColor.RED else CarColor.PRIMARY)
                .build())

            .setOnClickListener {
                handleActionClick()
            }
            .build()


        val builder = MessageTemplate.Builder(displayMessage)
            .setTitle("AI Assistant")
            .setHeaderAction(Action.APP_ICON)
            .addAction(actionButton)

        // Hiển thị vòng xoay loading khi AI đang "suy nghĩ" (gọi API Groq)
        if(currentState == AssistantState.THINKING){
            builder.setLoading(true)
        }

        return builder.build()
    }

    private fun handleActionClick() {
        when (currentState) {
            AssistantState.IDLE -> {
                startListening()
            }
            AssistantState.SPEAKING -> {
                tts?.stop() // dừng nói nếu người dùng nhấn nút stop
                updateState(AssistantState.IDLE, "Đã dừng. Nhấn mic để hỏi lại.")
            }
            else -> {
                //không làm gì khi đang nghe hoặc đang nghĩ
            }
        }
    }

    private fun updateState(state: AssistantState, message: String){
        currentState = state
        displayMessage = message
        invalidate()
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(carContext)
        recognizer.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                updateState(AssistantState.LISTENING, "Đang nghe...")
            }
            override fun onResults(results: Bundle?) {
                val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val textInput = data?.get(0) ?: ""
                processWithAI(textInput)
            }
            override fun onError(error: Int) {
                displayMessage = "Lỗi nhận diện. Hãy thử lại."
                invalidate()
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer.startListening(intent)
    }

    private fun processWithAI(input: String) {
        lifecycleScope.launch {
            // Chuyển sang trạng thái SUY NGHĨ (hiện vòng xoay)
            updateState(AssistantState.THINKING, "Đang suy nghĩ..")

            val aiResponse = GroqManager.chatWithAI(input)

            updateState(AssistantState.SPEAKING, aiResponse)


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