package com.example.myapplication

import android.net.Uri
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template

/**
 * Navigation screen showing destination information in Android Auto
 * Maps is launched separately from MyAiSession
 */
class NavigationScreen(
    carContext: CarContext,
    private val destination: String
) : Screen(carContext) {

    init {
        Log.d("NAV_SCREEN", "NavigationScreen created for: $destination")
    }

    override fun onGetTemplate(): Template {
        // Show navigation information on Android Auto display
        return MessageTemplate.Builder(
            "Điều hướng đến:\n\n$destination"
        )
            .setTitle("Bản đồ")
            .setHeaderAction(Action.APP_ICON)
            .addAction(
                Action.Builder()
                    .setTitle("Đóng")
                    .setOnClickListener { finish() }
                    .build()
            )
            .build()
    }
}


