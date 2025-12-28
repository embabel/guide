package com.embabel.hub

import java.time.Instant

data class StandardErrorResponse(
    val timestamp: Instant,
    val status: Int,
    val error: String,
    val message: String,
    val path: String
)