package com.embabel.guide.narrator

/**
 * Input to the narrator agent: the raw assistant message content.
 */
data class NarrationInput(val content: String)

/**
 * Classification of content complexity for narration routing.
 */
enum class NarrationCategory {
    /** Short plain text — pass through as-is */
    SIMPLE,
    /** Markdown with structure but no code blocks */
    COMPLEX,
    /** Markdown containing code blocks */
    COMPLEX_WITH_CODE
}

/**
 * Intermediate result after classification, carrying the original content
 * and its determined category.
 */
data class ClassifiedNarration(
    val content: String,
    val category: NarrationCategory
)

/**
 * Final TTS-friendly narration output.
 */
data class Narration(val text: String)
