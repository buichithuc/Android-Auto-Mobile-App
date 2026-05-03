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
        // Store navigation URI to pass to screen
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

// Custom Session class to handle navigation intents
class MyAiSessionCompat : androidx.car.app.Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        Log.d("NAV_SERVICE", "onCreateScreen called with intent: $intent")
        Log.d("NAV_SERVICE", "Intent action: ${intent.action}")
        
        // Only process navigation intents
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
                } catch (e: Exception) {
                    Log.e("NAV_SERVICE", "Error extracting navigation query: ${e.message}")
                }
            }
        }
        
        // Return the AI screen - it will check pendingNavigationUri in its init
        return MyAiScreen(carContext)
    }
}