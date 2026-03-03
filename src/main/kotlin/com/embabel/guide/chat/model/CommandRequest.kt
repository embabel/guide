package com.embabel.guide.chat.model

data class CommandRequest(
    val correlationId: String,
    val type: String,
    val value: String,
    val clearPrevious: Boolean = false,
)

data class CommandResponse(
    val correlationId: String,
    val success: Boolean,
    val message: String,
)
