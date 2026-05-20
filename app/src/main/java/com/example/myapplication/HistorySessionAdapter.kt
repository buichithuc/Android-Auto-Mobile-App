package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistorySessionAdapter(
    private val onSessionClick: (SessionMetadata) -> Unit,
    private val onSessionLongClick: (SessionMetadata) -> Unit
) : ListAdapter<SessionMetadata, HistorySessionAdapter.HistoryViewHolder>(SessionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history_session, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Ánh xạ View bằng findViewById giống hệt ChatAdapter của bạn, không sợ lỗi build nữa
        private val tvSessionTitle: TextView = view.findViewById(R.id.tvSessionTitle)
        private val tvSessionTime: TextView = view.findViewById(R.id.tvSessionTime)

        fun bind(session: SessionMetadata) {
            tvSessionTitle.text = session.title

            val sdf = SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault())
            tvSessionTime.text = sdf.format(Date(session.timestamp))

            // Bắt sự kiện chạm và giữ chặt
            itemView.setOnClickListener { onSessionClick(session) }
            itemView.setOnLongClickListener {
                onSessionLongClick(session)
                true
            }
        }
    }

    class SessionDiffCallback : DiffUtil.ItemCallback<SessionMetadata>() {
        override fun areItemsTheSame(oldItem: SessionMetadata, newItem: SessionMetadata): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SessionMetadata, newItem: SessionMetadata): Boolean {
            return oldItem.title == newItem.title && oldItem.timestamp == newItem.timestamp
        }
    }
}