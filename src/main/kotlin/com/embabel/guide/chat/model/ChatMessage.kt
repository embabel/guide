package com.embabel.guide.chat.model

data class ChatMessage(
    val toUserId: String? = null,
    val room: String? = null,
    val body: String
)
