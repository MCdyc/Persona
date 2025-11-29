package com.bytedance.persona.model

import java.util.UUID

enum class MessageType {
    TEXT,
    IMAGE,
    AUDIO
}

enum class Sender {
    USER,
    AI
}

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val content: String, // 对于文本是文字，对于图片/音频是 URL
    val sender: Sender,
    val type: MessageType = MessageType.TEXT,
    val isStreaming: Boolean = false, // 是否正在流式输出中
    val timestamp: Long = System.currentTimeMillis()
)