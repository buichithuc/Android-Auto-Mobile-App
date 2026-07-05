package com.example.myapplication

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ModelManagerActivity : AppCompatActivity() {

    private lateinit var rvModels: RecyclerView
    private lateinit var adapter: ModelAdapter
    private lateinit var modelDownloadManager: ModelDownloadManager

    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: return
            val prefs = context?.getSharedPreferences("AiCarPrefs", Context.MODE_PRIVATE) ?: return
            val modelId = prefs.getString("download_id_$id", null)

            if (modelId != null) {
                val model = ModelRegistry.supportedModels.find { it.id == modelId }
                if (model != null) {
                    if (model.isZip) {
                        Toast.makeText(context, "Đang giải nén ${model.name}, vui lòng đợi...", Toast.LENGTH_LONG).show()

                        lifecycleScope.launch(Dispatchers.IO) {
                            val zipFile = File(modelDownloadManager.getModelDirectory(), "${model.fileName}.zip")
                            val targetDir = File(modelDownloadManager.getModelDirectory(), model.fileName)
                            try {
                                modelDownloadManager.unzip(zipFile, targetDir)
                                zipFile.delete() // Xóa ZIP
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Cài đặt hoàn tất!", Toast.LENGTH_SHORT).show()
                                    adapter.notifyDataSetChanged()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Lỗi giải nén: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    } else {
                        Toast.makeText(context, "Tải xong ${model.name}!", Toast.LENGTH_SHORT).show()
                        adapter.notifyDataSetChanged()
                    }
                }
                prefs.edit().remove("download_id_$id").apply()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model_manager)

        modelDownloadManager = ModelDownloadManager(this)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        rvModels = findViewById(R.id.rvModels)
        rvModels.layoutManager = LinearLayoutManager(this)

        adapter = ModelAdapter(
            models = ModelRegistry.supportedModels,
            downloadManager = modelDownloadManager,
            onDownloadClick = { model ->
                Toast.makeText(this, "Bắt đầu tải ${model.name}. Trượt thanh thông báo để xem.", Toast.LENGTH_LONG).show()
                modelDownloadManager.downloadModel(model)
                adapter.notifyDataSetChanged()
            },
            onDeleteClick = { model ->
                if (modelDownloadManager.deleteModel(model)) {
                    Toast.makeText(this, "Đã xóa ${model.name}", Toast.LENGTH_SHORT).show()
                    adapter.notifyDataSetChanged()
                }
            },
            onSelectClick = { model ->
                modelDownloadManager.setActiveModelId(model.id)
                Toast.makeText(this, "Đã chuyển sang ${model.name}", Toast.LENGTH_SHORT).show()
                adapter.notifyDataSetChanged()
            }
        )
        rvModels.adapter = adapter
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadCompleteReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(downloadCompleteReceiver, filter)
        }
        adapter.notifyDataSetChanged()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(downloadCompleteReceiver)
    }
}