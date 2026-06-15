package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import androidx.core.widget.addTextChangedListener

class MainActivity : AppCompatActivity() {
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var adapter: ChatAdapter
    private lateinit var historyAdapter: HistorySessionAdapter
    private lateinit var rvChat: RecyclerView
    private lateinit var mainLayout: View
    private lateinit var inputArea: View
    private lateinit var rvHistorySessions: RecyclerView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var edtSearchHistory: TextInputEditText

    // KHAI BÁO TRẠM ĐIỀU PHỐI AI (TÍCH HỢP MỚI)
    private lateinit var aiManager: AIManager

    private var currentSearchKeyword = ""

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.getOrNull(0)

            if (!spokenText.isNullOrEmpty()) {
                val edtMessage = findViewById<EditText>(R.id.edtMessage)
                edtMessage.setText(spokenText)
                edtMessage.setSelection(spokenText.length)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        inputArea = findViewById(R.id.inputArea)
        toolbar = findViewById(R.id.toolbar)
        val dir = getExternalFilesDir(null)
        if (dir != null && !dir.exists()) {
            dir.mkdirs() // Ép hệ thống tạo thư mục
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.drawerLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())

            val toolbarParams = toolbar.layoutParams as android.view.ViewGroup.MarginLayoutParams
            toolbarParams.topMargin = systemBars.top
            toolbar.layoutParams = toolbarParams

            navigationView.setPadding(0, systemBars.top, 0, systemBars.bottom)

            val keyboardHeight = imeInsets.bottom - systemBars.bottom
            inputArea.translationY = if (keyboardHeight > 0) -keyboardHeight.toFloat() else 0f
            insets
        }

        // 1. KHỞI TẠO LÕI AI NGOẠI TUYẾN NGẦM
        aiManager = AIManager(this)
        lifecycleScope.launch {
            aiManager.initialize()
        }

        setupView()
        setupHistorySidebar()
        setupLocalAiSwitch() // Gọi hàm cài đặt công tắc chuyển đổi
        observeViewModel()
    }

    // HÀM TÍCH HỢP MỚI: Lắng nghe công tắc bật/tắt Local AI
    private fun setupLocalAiSwitch() {
        val switchLocalMode = findViewById<SwitchMaterial>(R.id.switchLocalMode)

        // Đọc trạng thái lưu trước đó
        val isLocalSaved = PreferenceHelper.isLocalModeEnabled(this)
        switchLocalMode.isChecked = isLocalSaved

        switchLocalMode.setOnCheckedChangeListener { _, isChecked ->
            PreferenceHelper.setLocalMode(this, isChecked)
            val modeMsg = if (isChecked) "Đã bật chế độ Ngoại tuyến (Bảo mật 100%)" else "Đã chuyển sang Đám mây (Gemini)"
            Toast.makeText(this, modeMsg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupView() {
        rvChat = findViewById(R.id.rvChat)
        val edtMessage = findViewById<EditText>(R.id.edtMessage)
        val btnSend = findViewById<ImageButton>(R.id.btnSend)
        val btnLogout = findViewById<ImageButton>(R.id.btnLogout)
        val btnMenu = findViewById<ImageButton>(R.id.btnMenu)
        val btnNewChat = findViewById<MaterialButton>(R.id.btnNewChat)
        val btnVoice = findViewById<ImageButton>(R.id.btnVoice)

        btnVoice.setOnClickListener {
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Bạn muốn hỏi AI điều gì?")
                }
                speechLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Thiết bị không hỗ trợ nhập liệu giọng nói", Toast.LENGTH_SHORT).show()
            }
        }

        adapter = ChatAdapter(viewModel.messages.value)
        rvChat.layoutManager = LinearLayoutManager(this)
        rvChat.adapter = adapter

        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(navigationView)
        }

        btnNewChat.setOnClickListener {
            viewModel.startNewChatSession()
            drawerLayout.closeDrawer(navigationView)
            Toast.makeText(this, "Đã bắt đầu phiên chat mới", Toast.LENGTH_SHORT).show()
        }

        // TÍCH HỢP MỚI: NÚT GỬI ĐƯỢC CHIA LÀM 2 LUỒNG
        btnSend.setOnClickListener {
            val text = edtMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                val isLocalMode = PreferenceHelper.isLocalModeEnabled(this@MainActivity)

                if (isLocalMode) {
                    // --- LUỒNG 1: XỬ LÝ NGOẠI TUYẾN BẢO MẬT (LOCAL LLM) ---
                    lifecycleScope.launch {
                        // Hiển thị tin nhắn của User lên UI lập tức
                        viewModel.addLocalMessageToUI(text, isUser = true)

                        // Lấy phản hồi từ lõi AI trong máy
                        val response = aiManager.getResponse(text, useLocalMode = true)

                        // Hiển thị phản hồi của AI lên UI
                        viewModel.addLocalMessageToUI(response, isUser = false)
                    }
                } else {
                    // --- LUỒNG 2: XỬ LÝ ĐÁM MÂY (GEMINI + FIREBASE) ---
                    viewModel.sendMessage(text)
                }

                edtMessage.text.clear()
            }
        }

        btnLogout.setOnClickListener {
            Firebase.auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
        }
    }

    private fun setupHistorySidebar() {
        rvHistorySessions = findViewById(R.id.rvHistorySessions)
        rvHistorySessions.layoutManager = LinearLayoutManager(this)
        edtSearchHistory = findViewById(R.id.edtSearchHistory)

        historyAdapter = HistorySessionAdapter(
            onSessionClick = { session ->
                viewModel.selectSession(session.id)
                drawerLayout.closeDrawer(navigationView)
            },
            onSessionLongClick = { session ->
                showDeleteConfirmDialog(session)
            }
        )
        rvHistorySessions.adapter = historyAdapter

        edtSearchHistory.addTextChangedListener { editable ->
            currentSearchKeyword = editable?.toString()?.trim()?.lowercase() ?: ""
            filterAndSubmitSessions(viewModel.sessions.value)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.messages.collect { updatedList ->
                adapter.updateData(updatedList)
                if (updatedList.isNotEmpty()) {
                    rvChat.smoothScrollToPosition(updatedList.size - 1)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.sessions.collect { sessionList ->
                historyAdapter.submitList(sessionList)
            }
        }
    }

    private fun filterAndSubmitSessions(fullList: List<SessionMetadata>) {
        if (currentSearchKeyword.isEmpty()) {
            historyAdapter.submitList(fullList)
        } else {
            val filtered = fullList.filter { session ->
                session.title.lowercase().contains(currentSearchKeyword)
            }
            historyAdapter.submitList(filtered)
        }
    }

    private fun showDeleteConfirmDialog(session: SessionMetadata) {
        AlertDialog.Builder(this)
            .setTitle("Xóa cuộc trò chuyện?")
            .setMessage("Hành động này sẽ xóa toàn bộ nội dung cuộc trò chuyện '${session.title}' khỏi hệ thống đám mây vĩnh viễn.")
            .setPositiveButton("Xóa") { _, _ ->
                viewModel.deleteSession(session.id)
                Toast.makeText(this, "Đã xóa hội thoại", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    // QUAN TRỌNG: Giải phóng bộ nhớ RAM khi thoát ứng dụng
    override fun onDestroy() {
        super.onDestroy()
        aiManager.close()
    }
}