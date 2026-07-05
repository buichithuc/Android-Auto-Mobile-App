package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.slider.Slider

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val rgTheme = findViewById<RadioGroup>(R.id.rgTheme)
        val rbDark = findViewById<RadioButton>(R.id.rbDark)
        val rbLight = findViewById<RadioButton>(R.id.rbLight)
        val sliderFontSize = findViewById<Slider>(R.id.sliderFontSize)
        val tvFontSizeLabel = findViewById<TextView>(R.id.tvFontSizeLabel)
        val tvPreview = findViewById<TextView>(R.id.tvPreview)
        val btnCancel = findViewById<Button>(R.id.btnCancel)
        val btnSave = findViewById<Button>(R.id.btnSave)

        val btnManageModels = findViewById<Button>(R.id.btnManageModels)

        // Đọc cài đặt hiện tại
        val prefs = getSharedPreferences("AiCarPrefs", Context.MODE_PRIVATE)
        val currentFontSize = prefs.getFloat("chat_font_size", 16f)
        val isDark = prefs.getBoolean("is_dark_mode", false)

        // Cập nhật giao diện theo cài đặt cũ
        if (isDark) rbDark.isChecked = true else rbLight.isChecked = true
        sliderFontSize.value = currentFontSize
        tvFontSizeLabel.text = "${currentFontSize.toInt()} sp"
        tvPreview.textSize = currentFontSize

        // Lắng nghe thao tác kéo thanh trượt
        sliderFontSize.addOnChangeListener { _, value, _ ->
            tvFontSizeLabel.text = "${value.toInt()} sp"
            tvPreview.textSize = value
        }

        btnManageModels.setOnClickListener {
            startActivity(Intent(this, ModelManagerActivity::class.java))
        }

        btnCancel.setOnClickListener { finish() }

        btnSave.setOnClickListener {
            val isDarkModeSelected = rbDark.isChecked

            // Lưu cài đặt
            prefs.edit()
                .putFloat("chat_font_size", sliderFontSize.value)
                .putBoolean("is_dark_mode", isDarkModeSelected)
                .apply()

            // Cập nhật lại giao diện ngay lập tức
            AppCompatDelegate.setDefaultNightMode(
                if (isDarkModeSelected) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
            finish()
        }
    }
}