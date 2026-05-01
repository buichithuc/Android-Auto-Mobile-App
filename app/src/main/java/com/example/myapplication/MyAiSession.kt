package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
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
import android.net.Uri
import androidx.core.content.ContextCompat


enum class AssistantState {
    STARTING,
    IDLE,
    WAITING_FOR_HOTWORD,
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
    private var displayMessage = "Chào bạn! tôi có thể giúp gì cho bạn"
    private var tts: TextToSpeech? = null

    // Lưu biến toàn cục để có thể destroy() bất cứ lúc nào, tránh xung đột Mic
    private var activeRecognizer: SpeechRecognizer? = null
    private var passiveRecognizer: SpeechRecognizer? = null
    private val HOTWORD = "trợ lý"

    private val messageReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
           val sender = intent?.getStringExtra("bundle_sender") ?: "Người dùng ẩn danh"
            val content = intent?.getStringExtra("bundle_content") ?: ""

            if(content.isNotEmpty()){
                lifecycleScope.launch {
                    processWithAI("Tóm tắt ngắn gọn tin nhắn từ $sender: $content")
                }
            }
        }
    }



    init {
        // Khởi tạo TTS an toàn
        tts = TextToSpeech(carContext, this)
        setupTtsListener()
        startPassiveListening()

        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                val filter = IntentFilter("COM_EXAMPLE_NEW_MESSAGE")

                // Với Android 14 trên máy realme của bạn, cần flag RECEIVER_EXPORTED
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    carContext.registerReceiver(messageReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    ContextCompat.registerReceiver(
                        carContext,
                        messageReceiver,
                        filter,
                        ContextCompat.RECEIVER_EXPORTED
                    )
                }
                Log.d("AI_DEBUG", "Đã đăng ký lắng nghe tin nhắn")
            }

            override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                try {
                    carContext.unregisterReceiver(messageReceiver)
                    Log.d("AI_DEBUG", "Đã hủy đăng ký lắng nghe tin nhắn")
                } catch (e: Exception) {
                    Log.e("AI_DEBUG", "Lỗi khi hủy receiver: ${e.message}")
                }
            }
        })

    }

    // TỰ ĐỘNG RESET KHI AI NÓI XONG
    private fun setupTtsListener() {
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                // Khi AI nói xong, đưa nút bấm về lại hình Mic (IDLE)
                carContext.mainExecutor.execute {
                    updateState(AssistantState.IDLE, displayMessage)
                    startPassiveListening()
                }
            }
            override fun onError(utteranceId: String?) {
                carContext.mainExecutor.execute {
                    updateState(AssistantState.IDLE, "Lỗi phát âm thanh.")
                    startPassiveListening()
                }
            }
        })
    }

    private fun startPassiveListening() {
        if(currentState != AssistantState.IDLE && currentState != AssistantState.WAITING_FOR_HOTWORD) return

        currentState = AssistantState.WAITING_FOR_HOTWORD

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        passiveRecognizer?.destroy()
        passiveRecognizer = SpeechRecognizer.createSpeechRecognizer(carContext)
        passiveRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle?) {
                val data = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = data?.get(0)?.lowercase() ?: ""

                if (text.contains(HOTWORD)) {
                    passiveRecognizer?.destroy()
                    // Kích hoạt phiên nghe thật sự
                    carContext.mainExecutor.execute { startListening() }
                }
            }

            override fun onError(error: Int) {
                passiveRecognizer?.destroy()
                // Nếu timeout (không ai nói gì), tự khởi động lại vòng lặp rảnh tay
                if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH) {
                    startPassiveListening()
                }
            }

            override fun onResults(results: Bundle?) {
                passiveRecognizer?.destroy()
                startPassiveListening()
            }
            override fun onReadyForSpeech(p0: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(p0: Float) {}
            override fun onBufferReceived(p0: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(p0: Int, p1: Bundle?) {}
        })


        passiveRecognizer?.startListening(intent)
    }


    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale("vi", "VN")
    }

    override fun onGetTemplate(): Template {
        val builder = MessageTemplate.Builder(displayMessage)
            .setTitle("AI Assistant")
            .setHeaderAction(Action.APP_ICON)


        when(currentState) {
            AssistantState.THINKING, AssistantState.STARTING-> {
                builder.setLoading(true)
            }

            AssistantState.LISTENING -> {

            }

            AssistantState.SPEAKING, AssistantState.IDLE, AssistantState.WAITING_FOR_HOTWORD -> {
                val iconRes = if (currentState == AssistantState.SPEAKING) R.drawable.ic_stop else R.drawable.ic_mic
                val iconColor = when(currentState) {
                    AssistantState.SPEAKING -> CarColor.RED
                    AssistantState.WAITING_FOR_HOTWORD -> CarColor.GREEN // Hoặc CarColor.SECONDARY
                    else -> CarColor.PRIMARY
                }

                val actionButton = Action.Builder()
                    .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, iconRes))
                    .setTint(iconColor)
                    .build())
                    .setOnClickListener { handleActionClick() }
                    .build()

                builder.addAction(actionButton)
            }

            else -> {

            }
        }

        if (currentState == AssistantState.SPEAKING && displayMessage.contains("\n")) {
            val paneBuilder = Pane.Builder()
            val lines = displayMessage.split("\n").filter { it.isNotBlank() }

            lines.take(4).forEach { line ->
                paneBuilder.addRow(
                    Row.Builder()
                        .setTitle(line.replace("- ", "").replace("* ", ""))
                        .build()
                )

            }

            val stopAction = Action.Builder()
                .setIcon(
                    CarIcon.Builder(
                        IconCompat.createWithResource(
                            carContext,
                            R.drawable.ic_stop
                        )
                    )
                        .setTint(CarColor.RED)
                        .build()
                )
                .setOnClickListener { handleActionClick() }
                .build()
            paneBuilder.addAction(stopAction)

            return PaneTemplate.Builder(paneBuilder.build())
                .setTitle("AI Phản hồi")
                .setHeaderAction(Action.APP_ICON)
                .build()
        }
        return builder.build()
    }

    private fun handleActionClick() {
        when (currentState) {
            AssistantState.IDLE, AssistantState.WAITING_FOR_HOTWORD -> {
                startListening()
            }
            AssistantState.SPEAKING -> {
                tts?.stop() // dừng nói nếu người dùng nhấn nút stop
                updateState(AssistantState.IDLE, "Đã dừng. Nhấn mic để hỏi lại.")
                startPassiveListening()
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

        passiveRecognizer?.destroy()
        activeRecognizer?.destroy()

        updateState(AssistantState.STARTING, "Mời bạn nói...")


        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        activeRecognizer = SpeechRecognizer.createSpeechRecognizer(carContext)
        activeRecognizer?.setRecognitionListener(object : RecognitionListener {

            override fun onPartialResults(partialResults: Bundle?){
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0) ?: ""
                if(text.isNotEmpty()) {
                    updateState(AssistantState.LISTENING, text)
                }
            }

            override fun onReadyForSpeech(params: Bundle?) {
                updateState(AssistantState.LISTENING, "Đang nghe...")
            }
            override fun onResults(results: Bundle?) {
                val finalInput = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0) ?: ""
                activeRecognizer?.destroy()
                if(finalInput.isNotEmpty()) processWithAI(finalInput)
                else startPassiveListening()
            }
            override fun onError(error: Int) {
                activeRecognizer?.destroy()
                updateState(AssistantState.IDLE, "Tôi không nghe rõ. Thử lại nhé?")
                startPassiveListening()
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        activeRecognizer?.startListening(intent)
    }

    private fun processWithAI(input: String) {
        val lowerInput = input.lowercase()
        if(lowerInput.contains("xóa lịch sử") || lowerInput.contains("làm mới cuộc trò chuyện")){
            GeminiManager.clearChatHistory()
            updateState(AssistantState.IDLE, "Lịch sử trò chuyện đã được làm mới.")
            tts?.speak("Đã xóa lịch sử trò chuyện", TextToSpeech.QUEUE_FLUSH, null, "ClearHistoryTTS")
            startPassiveListening()
            return
        }
        lifecycleScope.launch {
            try {
                // Chuyển sang trạng thái SUY NGHĨ (hiện vòng xoay)
                updateState(AssistantState.THINKING, "Đang suy nghĩ..")

                val aiResponse = GeminiManager.chatWithAI(input)

                val audioManager =
                    carContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

                if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()


                    val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(audioAttributes)
                        .build()

                    val result = audioManager.requestAudioFocus(focusRequest)

                    if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        //Kiểm tra ý định dẫn đường
                        if (aiResponse.startsWith("NAVIGATE_TO:")) {
                            val parts = aiResponse.split(".")
                            val destination = parts[0].replace("NAVIGATE_TO:", "").trim()
                            val speechText = parts.drop(1).joinToString(".").trim()
                            updateState(AssistantState.SPEAKING, speechText)
                            speakAndNavigate(speechText, destination)
                        } else {
                            updateState(AssistantState.SPEAKING, aiResponse)
                            tts?.speak(aiResponse, TextToSpeech.QUEUE_FLUSH, null, "GeminiTTS")
                        }
                    } else {
                        updateState(AssistantState.IDLE, aiResponse)
                    }
                }

            }catch(e: Exception){
                Log.e("AI_ERROR", "Lỗi gọi API: ${e.message}")
                updateState(AssistantState.IDLE, "Lỗi kết nối. Hãy thử lại sau.")
                startPassiveListening()
            }
        }
    }
    private fun speakAndNavigate(text: String, destination: String) {
        // 1. Phát âm thanh phản hồi trước
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "NavTTS")

        try {
            // 2. Làm sạch và mã hóa địa điểm (Quan trọng cho tiếng Việt có dấu/khoảng trắng)
            val encodedDestination = android.net.Uri.encode(destination.trim())
            val uri = android.net.Uri.parse("google.navigation:q=$encodedDestination")

            // 3. Tạo Intent với Action chuẩn của Android Auto
            val intent = Intent(CarContext.ACTION_NAVIGATE, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // 4. Thực hiện lệnh mở App bản đồ
            carContext.startCarApp(intent)

        } catch (e: Exception) {
            // Log lỗi chi tiết để debug trong Logcat
            Log.e("NAV_ERROR", "Lỗi điều hướng đến $destination: ${e.message}")

            // Thông báo lỗi bằng giọng nói để tài xế biết
            tts?.speak("Rất tiếc, tôi không thể mở bản đồ lúc này.", TextToSpeech.QUEUE_ADD, null, "NavErrorTTS")
        }
    }
}