package com.embabel.guide.chat.service

/**
 * Interface for integrating with RAG (Retrieval-Augmented Generation) systems.
 *
 * This adapter provides a non-blocking way to send messages to a RAG system
 * and receive responses, with support for real-time status events during processing.
 */
interface RagServiceAdapter {

    /**
     * Sends a message to the RAG system and returns the response.
     *
     * This is a suspending function to avoid blocking threads during potentially
     * long-running RAG operations (document retrieval, LLM inference, etc.).
     *
     * @param message The user's message to process
     * @param fromUserId The ID of the user sending the message (for context/logging)
     * @param onEvent Callback function to receive real-time status updates during processing
     *                (e.g., "Planning response", "Querying database", "Generating answer")
     * @return The RAG system's response message
     */
    suspend fun sendMessage(
        message: String,
        fromUserId: String,
        onEvent: (String) -> Unit = {}
    ): String
}