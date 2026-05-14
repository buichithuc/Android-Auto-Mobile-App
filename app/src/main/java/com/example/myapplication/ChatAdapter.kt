package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(private var messages: List<ChatMessage>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemViewType(position: Int) :Int{
        return if (messages[position].isUser) ChatViewType.USER else ChatViewType.AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == ChatViewType.USER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_user, parent, false)
            UserViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_ai, parent, false)
            AiViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        if (holder is UserViewHolder) holder.bind(message)
        else if (holder is AiViewHolder) holder.bind(message)
    }

    override fun getItemCount() = messages.size

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txt = view.findViewById<TextView>(R.id.txtMessageUser)
        fun bind(msg: ChatMessage) { txt.text = msg.text }
    }

    class AiViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txt = view.findViewById<TextView>(R.id.txtMessageAi)
        fun bind(msg: ChatMessage) { txt.text = msg.text }
    }

    fun updateData(newList: List<ChatMessage>){
        this.messages = newList
        notifyDataSetChanged()
    }

}

































