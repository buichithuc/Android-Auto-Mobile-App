package com.example.myapplication

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MessageListenerService : NotificationListenerService(){
    override fun onNotificationPosted(sbn: StatusBarNotification){
        val packageName = sbn.packageName
        val extras = sbn.notification.extras

        if(packageName.contains("telecom") || packageName.contains("mms") || packageName.contains("zalo")
            || packageName.contains("whatsapp")){
            val sender = extras.getString(Notification.EXTRA_TITLE) ?: "Người gửi ẩn danh"
            val content = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            // Gửi tín hiệu đến MyAiScreen
            val intent = Intent("COM_EXAMPLE_NEW_MESSAGE")
            intent.putExtra("bundle_sender", sender)
            intent.putExtra("bundle_content", content)
            sendBroadcast(intent)
        }
    }

}