package com.embabel.hub

/**
 * Audio effects that can be applied to a persona's narration voice.
 * Stored in Neo4j by enum name (e.g. "WARM", "ECHO").
 */
enum class AudioEffect(val displayName: String) {
    WARM("Warm"),
    RADIO("Radio"),
    CATHEDRAL("Cathedral"),
    ECHO("Echo"),
}