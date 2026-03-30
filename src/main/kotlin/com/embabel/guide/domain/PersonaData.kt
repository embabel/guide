package com.embabel.guide.domain

import com.embabel.hub.AudioEffect
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.drivine.annotation.NodeFragment
import org.drivine.annotation.NodeId

@NodeFragment(labels = ["Persona"])
@JsonIgnoreProperties(ignoreUnknown = true)
data class PersonaData(
    @NodeId
    val id: String,
    val name: String,
    val prompt: String,
    val description: String? = null,
    val voice: String? = null,
    val effects: List<AudioEffect>? = null,
    val isSystem: Boolean = false,
)