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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


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
    private var pendingNavDestination: String? = null
    private var pendingNavUri: Uri? = null

    // Lưu biến toàn cục để có thể destroy() bất cứ lúc nào, tránh xung đột Mic
    private var activeRecognizer: SpeechRecognizer? = null
    private var passiveRecognizer: SpeechRecognizer? = null
    private val HOTWORD = "trợ lý"

    private var locationManager: LocationManager? = null
    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null





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
        // Check if there's a pending navigation request from Android Auto
        val pendingNavUri = MyAiCarService.pendingNavigationUri
        if (pendingNavUri != null) {
            Log.d("NAV_SCREEN", "Processing pending navigation URI: $pendingNavUri")
            val query = pendingNavUri.getQueryParameter("q")
            if (query != null) {
                Log.d("NAV_SCREEN", "Navigation destination: $query")
                // Store for later when TTS is ready
                pendingNavDestination = query
                this.pendingNavUri = pendingNavUri
            }
            MyAiCarService.pendingNavigationUri = null
        }

        locationManager = carContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
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
                
                // Process pending navigation after screen starts (TTS should be ready by then)
                if (pendingNavDestination != null && tts != null && pendingNavUri != null) {
                    Log.d("NAV_SCREEN", "Starting pending navigation to: $pendingNavDestination")
                    startNavigation(pendingNavUri!!, pendingNavDestination!!)
                    pendingNavDestination = null
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

        if (lowerInput.contains("thời tiết") ||
            lowerInput.contains("trời") ||
            lowerInput.contains("mưa") ||
            lowerInput.contains("nắng")) {

            lifecycleScope.launch {
                handleWeatherRequest()
            }
            return  // Dừng để không gọi Gemini API
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
        // Phát âm thanh phản hồi
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "NavTTS")

        try {
            // Làm sạch và mã hóa địa điểm
            val encodedDestination = android.net.Uri.encode(destination.trim())
            val uri = android.net.Uri.parse("geo:0,0?q=$encodedDestination")
            
            Log.d("NAV_DEBUG", "Navigation request for: $destination")
            Log.d("NAV_DEBUG", "Encoded URI: $uri")
            
            // Start navigation - shows UI on Android Auto and provides voice feedback
            startNavigation(uri, destination)
            
            // Thông báo thêm bằng giọng nói
            tts?.speak("Đang mở bản đồ để điều hướng đến $destination", TextToSpeech.QUEUE_ADD, null, "NavInfoTTS")

        } catch (e: Exception) {
            Log.e("NAV_ERROR", "Lỗi xử lý điều hướng đến $destination: ${e.message}")
            tts?.speak("Rất tiếc, tôi không thể xử lý yêu cầu điều hướng lúc này.", TextToSpeech.QUEUE_ADD, null, "NavErrorTTS")
        }
    }


      //Show navigation screen in Android Auto
    private fun startNavigation(navigationUri: Uri, destination: String) {
        try {
            Log.d("NAV_SCREEN", "Starting navigation: $destination")
            
            // Show navigation screen in Android Auto with destination info
            val navigationScreen = NavigationScreen(carContext, destination)
            screenManager.push(navigationScreen)
            
            Log.d("NAV_SCREEN", "Navigation screen pushed successfully")
        } catch (e: Exception) {
            Log.e("NAV_SCREEN", "Error starting navigation: ${e.message}", e)
        }
    }

    // Hàm để lấy vị trí:
    private fun getCurrentLocation(): Pair<Double, Double>? {
        try {
            val location = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            return if (location != null) {
                Pair(location.latitude, location.longitude)
            } else {
                null
            }
        } catch (e: SecurityException) {
            Log.e("LOCATION_ERROR", "Permission denied: ${e.message}")
            return null
        }
    }

    private suspend fun handleWeatherRequest(){
        try{
            updateState(AssistantState.THINKING, "Đang kiểm tra thời tiết...")
            // 1. Lấy vị trí GPS
            val location = getCurrentLocation()
            if (location == null) {
                updateState(AssistantState.IDLE, "Không thể xác định vị trí. Vui lòng bật GPS.")
                startPassiveListening()
                return
            }
            // 2. Gọi API thời tiết
            val weatherData = WeatherManager.getWeather(
                latitude = location.first,
                longitude = location.second
            )

            if (weatherData == null) {
                updateState(AssistantState.IDLE, "Không thể lấy dữ liệu thời tiết.")
                startPassiveListening()
                return
            }

            // 3. Tạo phản hồi
            val response = buildWeatherResponse(weatherData)

            // 4. Phát âm thanh
            updateState(AssistantState.SPEAKING, response)
            requestAudioFocusAndSpeak(response)


        }catch(e: Exception){
            Log.e("WEATHER_ERROR", "Error: ${e.message}")
            updateState(AssistantState.IDLE, "Lỗi kiểm tra thời tiết.")
            startPassiveListening()
        }
    }
    private fun buildWeatherResponse(weather: WeatherManager.WeatherData): String {
        return """
        Hôm nay ở vị trí bạn:
        - Nhiệt độ: ${weather.temp}°C
        - Cảm giác: ${weather.feelsLike}°C
        - Tình trạng: ${weather.description}
        - Độ ẩm: ${weather.humidity}%
        - Gió: ${weather.windSpeed}m/s
    """.trimIndent()
    }

    private suspend fun requestAudioFocusAndSpeak(text: String){
        withContext(Dispatchers.Main){
            val audioManger = carContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttributes)
                .build()

            val result = audioManger.requestAudioFocus(focusRequest)
            if(result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "WeatherTTS")
            }


        }
    }

}






























