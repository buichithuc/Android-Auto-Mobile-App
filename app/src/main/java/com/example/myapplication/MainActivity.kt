package com.example.myapplication

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import android.widget.Toolbar
import androidx.activity.enableEdgeToEdge
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
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var adapter: ChatAdapter
    private lateinit var historyAdapter: HistorySessionAdapter // Adapter riêng cho Sidebar lịch sử
    private lateinit var rvChat: RecyclerView
    private lateinit var mainLayout: View
    private lateinit var inputArea: View
    private lateinit var rvHistorySessions: RecyclerView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: MaterialToolbar


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Ánh xạ các View quản lý cấu trúc DrawerLayout mới
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        inputArea = findViewById(R.id.inputArea)
        toolbar = findViewById(R.id.toolbar)

        //đẩy thanh nhập liệu lên trên bàn phím
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
       // Khởi tạo View
        setupView()
        // Khởi tạo danh sách lịch sử ở Sidebar
        setupHistorySidebar()
        //Lắng nghe dữ liệu từ ViewModel (Reactive UI)
        observeViewModel()

    }

    private fun setupView(){
        rvChat = findViewById(R.id.rvChat)
        val edtMessage = findViewById<EditText>(R.id.edtMessage)
        val btnSend = findViewById<ImageButton>(R.id.btnSend)
        val btnLogout = findViewById<ImageButton>(R.id.btnLogout)
        val btnMenu = findViewById<ImageButton>(R.id.btnMenu)
        val btnNewChat = findViewById<MaterialButton>(R.id.btnNewChat)


        adapter = ChatAdapter(viewModel.messages.value)
        rvChat.layoutManager = LinearLayoutManager(this)
        rvChat.adapter = adapter

        // Sự kiện bấm nút Hamburger -> Mở Sidebar trượt ra từ cạnh trái
        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(navigationView)
        }

        btnNewChat.setOnClickListener{
            viewModel.startNewChatSession()
            drawerLayout.closeDrawer(navigationView)
            Toast.makeText(this, "Đã bắt đầu phiên chat mới", Toast.LENGTH_SHORT).show()
        }

        btnSend.setOnClickListener{
            val text = edtMessage.text.toString().trim()
            if(text.isNotEmpty()){
                viewModel.sendMessage(text)
                edtMessage.text.clear()// Xóa nội dung sau khi gửi
            }
        }

        btnLogout.setOnClickListener{
            Firebase.auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
        }

    }

    private fun setupHistorySidebar(){
        rvHistorySessions = findViewById(R.id.rvHistorySessions)
        rvHistorySessions.layoutManager = LinearLayoutManager(this)

        // Khởi tạo Adapter nhận vào 2 hành động cụ thể khi click vào dòng lịch sử
        historyAdapter = HistorySessionAdapter(
            onSessionClick = { session ->
                // Hành động A: Click chọn cuộc trò chuyện cũ -> Load lại lịch sử
                viewModel.selectSession(session.id)
                drawerLayout.closeDrawer(navigationView) // Đóng Sidebar
            },
            onSessionLongClick = { session ->
                // Hành động B: Nhấn giữ chặt -> Hiện Dialog hỏi xác nhận xóa cuộc trò chuyện
                showDeleteConfirmDialog(session)
            }
        )
        rvHistorySessions.adapter = historyAdapter

    }

    private fun observeViewModel(){
        //Luồng 1: Lắng nghe danh sách tin nhắn chat để cập nhật giao diện chính
        lifecycleScope.launch{
            viewModel.messages.collect{updatedList ->
                // Cập nhật dữ liệu cho Adapter
                adapter.updateData(updatedList)

                // Tự động cuộn xuống tin nhắn cuối cùng
                if(updatedList.isNotEmpty()){
                    rvChat.smoothScrollToPosition(updatedList.size - 1)
                }

            }
        }
        // Luồng 2: Lắng nghe danh sách các Session từ Firestore đổ vào Sidebar
        lifecycleScope.launch{
            viewModel.sessions.collect { sessionList ->
                historyAdapter.submitList(sessionList)
            }
        }
    }

    // Hàm hiển thị hộp thoại xác nhận xóa hội thoại trên Đám mây
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
}


































