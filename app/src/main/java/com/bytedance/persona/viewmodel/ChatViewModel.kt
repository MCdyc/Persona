package com.bytedance.persona.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bytedance.persona.model.Message
import com.bytedance.persona.model.MessageType
import com.bytedance.persona.model.Sender
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    // 使用 StateList 驱动列表 UI 更新
    private val _messages = mutableStateListOf<Message>()
    val messages: List<Message> = _messages

    init {
        // 初始欢迎语
        addMessage(
            Message(
                content = "你好！我是 **Persona**。\n我可以和你聊天，也可以尝试 *画画* 或 *唱歌* 哦！\n试试发送 `画一只猫` 或者 `唱首歌`。",
                sender = Sender.AI
            )
        )
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        // 1. 添加用户消息
        addMessage(Message(content = text, sender = Sender.USER))

        // 2. 模拟 AI 思考和回复
        viewModelScope.launch {
            // 模拟网络延迟
            delay(500)
            generateAIResponse(text)
        }
    }

    private suspend fun generateAIResponse(userText: String) {
        val lowerText = userText.lowercase()

        when {
            // --- 多模态交互：文生图 ---
            lowerText.contains("画") || lowerText.contains("图") || lowerText.contains("image") -> {
                val loadingMsg = Message(content = "正在生成艺术作品...", sender = Sender.AI)
                addMessage(loadingMsg)
                delay(1500)
                // 移除 "正在生成"
                _messages.remove(loadingMsg)

                // 添加图片消息 (使用 Lorem Picsum 作为 Mock 数据源)
                // 实际项目中这里会调用 Stable Diffusion 或 DALL-E API
                addMessage(
                    Message(
                        content = "https://picsum.photos/400/300?random=${System.currentTimeMillis()}",
                        sender = Sender.AI,
                        type = MessageType.IMAGE
                    )
                )
                // 图片通常伴随一段文本
                streamTextResponse("这是为你生成的图片，希望你喜欢！🎨")
            }

            // --- 多模态交互：文生语音/音乐 ---
            lowerText.contains("唱") || lowerText.contains("歌") || lowerText.contains("music") -> {
                streamTextResponse("咳咳，让我为你高歌一曲... 🎵")
                delay(1000)
                addMessage(
                    Message(
                        content = "Persona_Symphony_No1.mp3", // Mock 音频
                        sender = Sender.AI,
                        type = MessageType.AUDIO
                    )
                )
            }

            // --- 基础对话 & 体验优化：Markdown + 流式输出 ---
            else -> {
                // 模拟一段包含 Markdown 的回复
                val responseText = mockAIResponseText(userText)
                streamTextResponse(responseText)
            }
        }
    }

    // 实现“打字机”流式效果
    private suspend fun streamTextResponse(targetText: String) {
        // 1. 先创建一个空的 AI 消息占位
        val messageId = java.util.UUID.randomUUID().toString()
        var currentContent = ""
        val initialMessage = Message(
            id = messageId,
            content = "",
            sender = Sender.AI,
            isStreaming = true
        )
        addMessage(initialMessage)

        // 2. 逐字更新消息内容
        for (char in targetText) {
            currentContent += char
            updateMessageContent(messageId, currentContent)
            // 模拟打字速度，标点符号停顿稍长
            val delayTime = if (char in listOf('，', '。', '！', '？', '\n')) 50L else 30L
            delay(delayTime)
        }

        // 3. 标记流式结束
        markMessageStreamingFinished(messageId)
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

    // 简单的 Mock 回复生成器
    private fun mockAIResponseText(input: String): String {
        return if (input.contains("markdown") || input.contains("格式")) {
            "没问题！Persona 支持 **Markdown** 格式。\n\n你可以看到 **加粗**，*斜体*，甚至 `代码样式`。\n这让阅读体验 *更棒* 了！"
        } else {
            "我听到了你说：“**$input**”。\n\n这真是一个 *有趣* 的话题！作为你的 **Persona**，我正在不断学习如何更好地与你交流。"
        }
    }
}