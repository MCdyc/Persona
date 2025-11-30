package com.bytedance.persona.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bytedance.persona.llm.LlmModel
import com.bytedance.persona.llm.LlmProvider
import com.bytedance.persona.llm.LlmRepository
import com.bytedance.persona.model.Message
import com.bytedance.persona.model.Sender
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class ChatViewModel(
    private val llmProvider: LlmProvider,
    private val llmRepository: LlmRepository
) : ViewModel() {

    private val _messages = mutableStateListOf<Message>()
    val messages: List<Message> = _messages

    val llmModels = llmRepository.models

    var selectedLlm = mutableStateOf<LlmModel?>(null)
        private set

    init {
        addMessage(
            Message(
                content = "你好！我是 **Persona**。\n我可以和你聊天，也可以帮你做很多事，请尽情吩咐！",
                sender = Sender.AI
            )
        )
        viewModelScope.launch {
            // Since the repository loads models asynchronously,
            // we wait until the list is populated before selecting the first model.
            while (llmRepository.models.isEmpty()) {
                kotlinx.coroutines.delay(100) // A simple delay to wait for models
            }
            selectedLlm.value = llmRepository.models.firstOrNull()
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val currentModel = selectedLlm.value
        if (currentModel == null) {
            // Handle case where no model is selected yet
            addMessage(Message(content = "正在加载模型，请稍候...", sender = Sender.AI))
            return
        }

        addMessage(Message(content = text, sender = Sender.USER))

        viewModelScope.launch {
            streamTextResponse(text, currentModel)
        }
    }

    private suspend fun streamTextResponse(prompt: String, model: LlmModel) {
        val messageId = java.util.UUID.randomUUID().toString()
        val initialMessage = Message(
            id = messageId,
            content = "",
            sender = Sender.AI,
            isStreaming = true
        )
        addMessage(initialMessage)

        var currentContent = ""
        llmProvider.generateResponse(prompt, model)
            .onStart { /* Optionally handle start of stream */ }
            .catch { e ->
                updateMessageContent(messageId, "出错了: ${e.message ?: "未知错误"}")
            }
            .onCompletion {
                markMessageStreamingFinished(messageId)
            }
            .collect { chunk ->
                currentContent += chunk
                updateMessageContent(messageId, currentContent)
            }
    }

    private fun addMessage(message: Message) {
        _messages.add(message)
    }

    private fun updateMessageContent(id: String, newContent: String) {
        val index = _messages.indexOfFirst { it.id == id }
        if (index != -1) {
            _messages[index] = _messages[index].copy(content = newContent)
        }
    }

    private fun markMessageStreamingFinished(id: String) {
        val index = _messages.indexOfFirst { it.id == id }
        if (index != -1) {
            _messages[index] = _messages[index].copy(isStreaming = false)
        }
    }

    fun selectLlm(llm: LlmModel) {
        selectedLlm.value = llm
        addMessage(Message(content = "模型已切换为: **${llm.name}**", sender = Sender.AI))
    }

    fun addLlm(model: LlmModel) {
        llmRepository.addModel(model)
    }

    fun updateLlm(model: LlmModel) {
        llmRepository.updateModel(model)
        if (selectedLlm.value?.id == model.id) {
            selectedLlm.value = model
        }
    }

    fun removeLlm(model: LlmModel) {
        if (llmRepository.models.size <= 1) return

        if (selectedLlm.value?.id == model.id) {
            selectLlm(llmRepository.models.first { it.id != model.id })
        }
        llmRepository.removeModel(model)
    }
}

class ChatViewModelFactory(
    private val llmProvider: LlmProvider,
    private val llmRepository: LlmRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(llmProvider, llmRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
