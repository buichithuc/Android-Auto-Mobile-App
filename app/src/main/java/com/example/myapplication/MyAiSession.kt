package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
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
import com.google.android.gms.common.util.CollectionUtils.listOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.Query



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
        return ConversationListScreen(carContext)
    }
}

// SCREEN (GIAO DIỆN & LOGIC)
class MyAiScreen(carContext: CarContext, private val sessionId: String? = null) : Screen(carContext), TextToSpeech.OnInitListener {
    private var currentState = if (sessionId != null) AssistantState.STARTING else AssistantState.IDLE
    private var displayMessage = if (sessionId != null) "Đang đồng bộ dữ liệu cuộc trò chuyện..." else "Chào bạn! tôi có thể giúp gì cho bạn"
    private var tts: TextToSpeech? = null
    private var pendingNavDestination: String? = null
    private var pendingNavUri: Uri? = null

    // Quản lý AI (Local + Cloud)
    private val aiManager = AIManager(carContext)

    // Lưu biến toàn cục để có thể destroy() bất cứ lúc nào, tránh xung đột Mic
    private var activeRecognizer: SpeechRecognizer? = null
    private var passiveRecognizer: SpeechRecognizer? = null
    private val HOTWORD = "trợ lý"

    private var locationManager: LocationManager? = null
    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null

    private var activeSessionId = sessionId ?: java.util.UUID.randomUUID().toString()
    private var isNewSession = (sessionId == null)
    private val db = com.google.firebase.Firebase.firestore
    private val auth = com.google.firebase.Firebase.auth


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

        locationManager = carContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Khởi tạo TTS an toàn
        tts = TextToSpeech(carContext, this)
        setupTtsListener()
        startPassiveListening()

        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                lifecycleScope.launch {
                    aiManager.initialize() // Khởi tạo AI Engine
                }

                val filter = IntentFilter("COM_EXAMPLE_NEW_MESSAGE")

                // Với Android 14 cần flag RECEIVER_EXPORTED
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

                if (sessionId != null) {
                    loadConversationContext(sessionId)
                }

            }

            override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                try {
                    carContext.unregisterReceiver(messageReceiver)
                    Log.d("AI_DEBUG", "Đã hủy đăng ký lắng nghe tin nhắn")
                } catch (e: Exception) {
                    Log.e("AI_DEBUG", "Lỗi khi hủy receiver: ${e.message}")
                }
            }

            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                aiManager.close()
                tts?.stop()
                tts?.shutdown()
            }
        })

    }

    // TỰ ĐỘNG RESET KHI AI NÓI XONG
    private fun setupTtsListener() {
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                carContext.mainExecutor.execute {
                    isSpeaking = false
                    processQueue() // Đọc câu tiếp theo trong hàng đợi
                    if (ttsQueue.isEmpty()) {
                        updateState(AssistantState.IDLE, displayMessage)
                    }
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
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("vi", "VN")
            tts?.setSpeechRate(0.95f) // Chậm lại một chút (1.0 là bình thường)
            tts?.setPitch(0.9f)       // Giọng trầm xuống một chút nghe sẽ bớt "sắc" hơn
        }
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

    private val ttsQueue = java.util.LinkedList<String>()
    private var isSpeaking = false

    private fun cleanTextForTTS(text: String): String {
        return text
            // Loại bỏ các thẻ in đậm/in nghiêng nếu Gemini vẫn vô tình lọt vào
            .replace(Regex("\\*\\*.*?\\*\\*"), "")
            .replace(Regex("[*#`\\[\\]]"), "")
            // Đổi dấu xuống dòng thành dấu cách để TTS không bị ngắt quãng giữa các gạch đầu dòng
            .replace("\n", " ")
            .replace("...", " ")
            .replace("—", " ")
            .replace(":", " ")
            .replace(",", " ")
            // Rút gọn nhiều khoảng trắng liên tiếp thành 1 khoảng trắng
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun speakSentence(text: String) {
        if (text.isEmpty()) return

        synchronized(ttsQueue) {
            ttsQueue.add(text) // Không cần gọi cleanTextForTTS ở đây nữa vì đã clean ở bước 2
        }
        processQueue()
    }

    private fun processQueue() {
        if (isSpeaking || ttsQueue.isEmpty()) return

        val nextSentence = synchronized(ttsQueue) { ttsQueue.poll() }
        isSpeaking = true

        // Dùng mã định danh duy nhất để biết khi nào câu này đọc xong
        val utteranceId = "TTS_${System.currentTimeMillis()}"
        tts?.speak(nextSentence, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }


    private fun processWithAI(input: String) {
        val lowerInput = input.lowercase()

        lifecycleScope.launch {
            try {

                val uid = auth.currentUser?.uid
                if(uid == null){
                    updateState(AssistantState.IDLE, "Vui lòng đăng nhập trên điện thoại để tiếp tục")
                    tts?.speak("Bạn đã đăng xuất. Vui lòng đăng nhập trên ứng dụng điện thoại để sử dụng AI.", TextToSpeech.QUEUE_FLUSH, null, "LogoutError")

                    startPassiveListening()
                    return@launch
                }

                val currentTime = System.currentTimeMillis()

                // Chuyển sang trạng thái SUY NGHĨ (hiện vòng xoay)
                updateState(AssistantState.THINKING, "Đang suy nghĩ..")

                if(isNewSession){
                    val sessionTitle = if (input.trim().length > 25) input.trim().take(25) + "..." else input.trim()
                    val sessionData = hashMapOf(
                        "title" to sessionTitle,
                        "timestamp" to currentTime
                    )

                    db.collection("users").document(uid)
                        .collection("sessions").document(activeSessionId)
                        .set(sessionData)
                    isNewSession = false
                }

                //Lưu câu hỏi của tài xế (Từ Micro ô tô dịch ra) lên Cloud Firestore
                val userMsg = ChatMessage(input.trim(), true, currentTime)
                db.collection("users").document(uid)
                    .collection("sessions").document(activeSessionId)
                    .collection("messages").add(userMsg)


                var fullResponse = ""
                val ttsBuffer = StringBuilder()
                var isNavigating = false


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

                        // 1. Đọc lựa chọn của người dùng từ bộ nhớ
                        val isLocalSelected = PreferenceHelper.isLocalModeEnabled(carContext)

                        // 2. Gọi getResponseStream từ aiManager
                        aiManager.getResponseStream(input.trim(), isLocalSelected).collect { chunk ->
                            fullResponse += chunk
                            ttsBuffer.append(chunk)

                            updateState(AssistantState.SPEAKING, fullResponse)
                            val currentText = ttsBuffer.toString()
                            val match = Regex("([.!?])(?:\\s|\$)").findAll(currentText).lastOrNull()

                            if (match != null) {
                                // Lấy vị trí cắt câu
                                val splitIndex = match.range.last

                                // Cắt từ đầu đến hết dấu câu
                                val sentenceToSpeak = currentText.substring(0, splitIndex + 1)

                                // Dọn dẹp câu trước khi đọc
                                val cleanSentence = cleanTextForTTS(sentenceToSpeak)

                                // Bỏ qua các câu quá ngắn (ví dụ: "1.", "À.") để tránh vấp nhịp
                                if (cleanSentence.isNotBlank() && cleanSentence.length > 3) {
                                    speakSentence(cleanSentence)
                                }

                                // Xóa đoạn đã đọc khỏi bộ đệm, giữ lại phần chưa thành câu
                                ttsBuffer.delete(0, splitIndex + 1)
                            }
                            // XỬ LÝ PHẦN CẶN (Khi Stream kết thúc mà chưa có dấu chấm câu)
                            val leftover = cleanTextForTTS(ttsBuffer.toString())
                            if (leftover.isNotBlank()) {
                                speakSentence(leftover)
                                ttsBuffer.setLength(0) // Xóa sạch bộ đệm
                            }

                        }


                        val aiTime = System.currentTimeMillis()
                        val aiMsg = ChatMessage(fullResponse, false, aiTime)

                        // Lưu câu trả lời hoàn chỉnh lên Cloud Firestore
                        db.collection("users").document(uid)
                            .collection("sessions").document(activeSessionId)
                            .collection("messages").add(aiMsg)

                        db.collection("users").document(uid)
                            .collection("sessions").document(activeSessionId)
                            .update("timestamp", aiTime)
                    }
                }else{
                    updateState(AssistantState.IDLE, "Không thể lấy quyền âm thanh")
                }

            }catch(e: Exception){
                Log.e("AI_ERROR", "Lỗi gọi API: ${e.message}")
                updateState(AssistantState.IDLE, "Lỗi kết nối. Hãy thử lại sau.")
                startPassiveListening()
            }
        }
    }


    private suspend fun requestAudioFocusAndSpeak(text: String){
        withContext(Dispatchers.Main){
            val audioManager = carContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttributes)
                .build()

            val result = audioManager.requestAudioFocus(focusRequest)
            if(result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "WeatherTTS")
            }


        }
    }

    private fun loadConversationContext(id: String){
        val uid = Firebase.auth.currentUser?.uid
        if(uid == null){
            updateState(AssistantState.IDLE, "Vui lòng đăng nhập để xem lịch sử.")
            tts?.speak("Không thể tải dữ liệu vì bạn chưa đăng nhập.", TextToSpeech.QUEUE_FLUSH, null, "AuthErrorTTS")
            startPassiveListening()
            return
        }

        updateState(AssistantState.STARTING, "Đang đồng bộ dữ liệu cuộc trò chuyện...")
        Firebase.firestore.collection("users").document(uid)
            .collection("sessions").document(id)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener{ docs ->

                val history = docs.documents.map { doc ->
                    val text = doc.getString("text") ?: ""
                    val timestamp = doc.getLong("timestamp") ?: 0L
                    val isUser = doc.getBoolean("isUser") ?: doc.getBoolean("user") ?: true
                    ChatMessage(text, isUser, timestamp)
                }

                lifecycleScope.launch {
                    GeminiManager.resumeChatSession(history)

                    updateState(AssistantState.IDLE, "Đã đồng bộ cuộc trò chuyện cũ. Mời bạn nói.")
                    tts?.speak("Đã kết nối dữ liệu đám mây. Tôi sẵn sàng lắng nghe câu hỏi tiếp theo", TextToSpeech.QUEUE_FLUSH, null, "ResumeSuccessTTS")
                    startPassiveListening()
                }
            }
            .addOnFailureListener { e ->
                Log.e("FIRESTORE_RESUME_ERROR", "Lỗi nạp ngữ cảnh: ${e.message}")
                updateState(AssistantState.IDLE, "Không thể kết nối dữ liệu cũ. Hãy thử lại.")
                startPassiveListening()
            }
    }


}