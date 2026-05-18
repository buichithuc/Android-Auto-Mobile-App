package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class MyAiCarService : CarAppService() {
    companion object {
        var pendingNavigationUri: Uri? = null
    }

    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        Log.d("NAV_SERVICE", "onCreateSession called")
        return MyAiSessionCompat()
    }
}

class MyAiSessionCompat : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        Log.d("NAV_SERVICE", "onCreateScreen called with intent: $intent")
        Log.d("NAV_SERVICE", "Intent action: ${intent.action}")

        // Tạo biến cờ hiệu để kiểm tra xem có phải luồng dẫn đường không
        var isNavIntent = false

        if (intent.action == CarContext.ACTION_NAVIGATE ||
            intent.action == "androidx.car.app.action.NAVIGATE") {
            Log.d("NAV_SERVICE", "Navigation intent detected")
            Log.d("NAV_SERVICE", "Intent data: ${intent.data}")

            val destUri = intent.data
            if (destUri != null) {
                try {
                    val query = destUri.getQueryParameter("q")
                    Log.d("NAV_SERVICE", "Navigation destination query: $query")
                    MyAiCarService.pendingNavigationUri = destUri

                    // Kích hoạt cờ hiệu nếu lấy URI thành công
                    isNavIntent = true
                } catch (e: Exception) {
                    Log.e("NAV_SERVICE", "Error extracting navigation query: ${e.message}")
                }
            }
        }

        // Quyết định màn hình xuất hiện dựa vào hành vi của tài xế
        return if (isNavIntent) {
            // Tài xế dùng giọng nói Google Assistant lệnh "Dẫn đường bằng MyAiApplication"
            // Vào thẳng màn hình Chat để xử lý bản đồ
            MyAiScreen(carContext)
        } else {
            // Tài xế chạm vào Icon ứng dụng trên màn hình xe hơi để mở app bình thường
            // Hiện danh sách 6 cuộc trò chuyện gần nhất từ Cloud Firestore lên trước
            ConversationListScreen(carContext)
        }
    }
}