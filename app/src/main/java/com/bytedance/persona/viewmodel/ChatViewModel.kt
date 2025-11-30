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

class ChatViewModel(private val llmProvider: LlmProvider) : ViewModel() {

    private val _messages = mutableStateListOf<Message>()
    val messages: List<Message> = _messages

    // 从 Repository 获取模型列表
    val llmModels = LlmRepository.models

    // 当前选择的 LLM，类型已更新为 LlmModel
    var selectedLlm = mutableStateOf(LlmRepository.models.first())
        private set

    init {
        addMessage(
            Message(
                content = "你好！我是 **Persona**。\n我可以和你聊天，也可以帮你做很多事，请尽情吩咐！",
                sender = Sender.AI
            )
        )
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        addMessage(Message(content = text, sender = Sender.USER))

        viewModelScope.launch {
            streamTextResponse(text)
        }
    }

    private suspend fun streamTextResponse(prompt: String) {
        val messageId = java.util.UUID.randomUUID().toString()
        val initialMessage = Message(
            id = messageId,
            content = "",
            sender = Sender.AI,
            isStreaming = true
        )
        addMessage(initialMessage)

        var currentContent = ""
        llmProvider.generateResponse(prompt, selectedLlm.value)
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

    // 方法参数已更新为 LlmModel
    fun selectLlm(llm: LlmModel) {
        selectedLlm.value = llm
        addMessage(Message(content = "模型已切换为: **${llm.name}**", sender = Sender.AI))
    }

    // --- 新增的模型管理函数 ---
    fun addLlm(model: LlmModel) {
        LlmRepository.addModel(model)
    }

    fun updateLlm(model: LlmModel) {
        LlmRepository.updateModel(model)
        // 如果更新的是当前选中的模型，则同步更新状态
        if (selectedLlm.value.id == model.id) {
            selectedLlm.value = model
        }
    }

    fun removeLlm(model: LlmModel) {
        // 至少保留一个模型
        if (LlmRepository.models.size <= 1) return

        // 如果删除的是当前选中的模型，则切换到另一个模型
        if (selectedLlm.value.id == model.id) {
            selectLlm(LlmRepository.models.first { it.id != model.id })
        }
        LlmRepository.removeModel(model)
    }
}

// ViewModel Factory 无需改动
class ChatViewModelFactory(private val llmProvider: LlmProvider) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(llmProvider) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
