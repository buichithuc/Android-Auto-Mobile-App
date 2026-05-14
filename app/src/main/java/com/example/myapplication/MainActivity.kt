package com.example.myapplication

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var adapter: ChatAdapter
    private lateinit var rvChat: RecyclerView

    private lateinit var mainLayout: View
    private lateinit var inputArea: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        mainLayout = findViewById(R.id.main)
        inputArea = findViewById(R.id.inputArea)

        //đẩy thanh nhập liệu lên trên bàn phím
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())

            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            val keyboardHeight = imeInsets.bottom - systemBars.bottom
            inputArea.translationY = if (keyboardHeight > 0) -keyboardHeight.toFloat() else 0f
            insets
        }
       // Khởi tạo View
        setupView()
        //Lắng nghe dữ liệu từ ViewModel (Reactive UI)
        observeViewModel()

    }

    private fun setupView(){
        rvChat = findViewById(R.id.rvChat)
        val edtMessage = findViewById<EditText>(R.id.edtMessage)
        val btnSend = findViewById<ImageButton>(R.id.btnSend)
        val btnLogout = findViewById<ImageButton>(R.id.btnLogout)


        adapter = ChatAdapter(viewModel.messages.value)
        rvChat.layoutManager = LinearLayoutManager(this)
        rvChat.adapter = adapter

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

    private fun observeViewModel(){
        lifecycleScope.launch{
            viewModel.messages.collect{updatedList ->
                // Cập nhật dữ liệu cho Adapter
                adapter.updateData(updatedList)
                rvChat.adapter = adapter


                // Tự động cuộn xuống tin nhắn cuối cùng
                if(updatedList.isNotEmpty()){
                    rvChat.smoothScrollToPosition(updatedList.size - 1)
                }

            }
        }
    }
}


































