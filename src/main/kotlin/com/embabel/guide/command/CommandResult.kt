package com.embabel.guide.command

/**
 * Structured output from pass 2's final LLM response after tool execution.
 *
 * @param summary composed from tool results, e.g. lines of checkmarks/crosses
 * @param ragRequest leftover question needing RAG, if any
 */
data class CommandResult(
    val summary: String,
    val ragRequest: String? = null,
)
