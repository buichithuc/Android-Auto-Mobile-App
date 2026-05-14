package com.example.myapplication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel(){
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping

    fun sendMessage(content: String){
        if(content.isNotEmpty()){

            val userMsg = ChatMessage(content, true)
            _messages.value = _messages.value + userMsg

            _isTyping.value = true

            viewModelScope.launch{
                try{
                    val response = GeminiManager.chatWithAI(content)
                    _messages.value = _messages.value + ChatMessage(response, false)

                }catch(e : Exception){
                    _messages.value = _messages.value + ChatMessage("Lỗi kết nối AI.", false)
                }finally {
                    _isTyping.value = false
                }
            }


        }
    }



}