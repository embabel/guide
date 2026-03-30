package com.embabel.hub

data class PersonaDto(
    val id: String,
    val name: String,
    val description: String?,
    val voice: String?,
    val effects: List<AudioEffect>?,
    val isSystem: Boolean,
    val isOwn: Boolean,
)
