package com.example.myapplication

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ModelAdapter(
    private val models: List<AiModel>,
    private val downloadManager: ModelDownloadManager,
    private val onDownloadClick: (AiModel) -> Unit,
    private val onDeleteClick: (AiModel) -> Unit,
    private val onSelectClick: (AiModel) -> Unit
) : RecyclerView.Adapter<ModelAdapter.ModelViewHolder>() {

    inner class ModelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvModelName)
        val tvDesc: TextView = view.findViewById(R.id.tvModelDesc)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val btnAction: ImageButton = view.findViewById(R.id.btnAction)
        val radioSelect: RadioButton = view.findViewById(R.id.radioSelect)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_model, parent, false)
        return ModelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
        val model = models[position]
        holder.tvName.text = model.name
        holder.tvDesc.text = model.description

        val isDownloaded = downloadManager.isModelDownloaded(model)
        val activeModelId = downloadManager.getActiveModelId()

        if (isDownloaded) {
            holder.tvStatus.text = "Đã tải sẵn trong máy"
            holder.tvStatus.setTextColor(Color.parseColor("#388E3C")) // Xanh lá
            holder.btnAction.setImageResource(android.R.drawable.ic_menu_delete) // Nút Xóa
            holder.btnAction.setColorFilter(Color.parseColor("#D32F2F"))

            holder.radioSelect.visibility = View.VISIBLE
            holder.radioSelect.isChecked = (model.id == activeModelId)

            holder.btnAction.setOnClickListener { onDeleteClick(model) }
            holder.radioSelect.setOnClickListener { onSelectClick(model) }
        } else {
            holder.tvStatus.text = "Chưa tải (Cần WiFi)"
            holder.tvStatus.setTextColor(Color.parseColor("#D32F2F")) // Đỏ
            holder.btnAction.setImageResource(android.R.drawable.stat_sys_download) // Nút Tải
            holder.btnAction.setColorFilter(Color.parseColor("#1976D2"))

            holder.radioSelect.visibility = View.GONE
            holder.btnAction.setOnClickListener { onDownloadClick(model) }
        }
    }

    override fun getItemCount() = models.size
}