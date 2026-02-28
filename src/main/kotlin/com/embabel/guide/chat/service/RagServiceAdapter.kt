package com.embabel.guide.chat.service

import com.embabel.chat.ChatTrigger

/**
 * Interface for integrating with RAG (Retrieval-Augmented Generation) systems.
 *
 * This adapter provides a non-blocking way to send messages to a RAG system
 * and receive responses, with support for real-time status events during processing.
 *
 * Conversation history is automatically managed by the underlying chatbot using
 * persistent conversation storage - callers don't need to track prior messages.
 */
interface RagServiceAdapter {

    /**
     * Sends a message to the RAG system and returns the response.
     * Each thread maintains its own conversation context with automatic history persistence.
     *
     * This is a suspending function to avoid blocking threads during potentially
     * long-running RAG operations (document retrieval, LLM inference, etc.).
     *
     * @param threadId The thread/conversation ID - used for session persistence and restoration
     * @param message The user's message to process
     * @param fromUserId The ID of the user sending the message (for context/logging)
     * @param onEvent Callback function to receive real-time status updates during processing
     *                (e.g., "Planning response", "Querying database", "Generating answer")
     * @return The RAG system's response message
     */
    suspend fun sendMessage(
        threadId: String,
        message: String,
        fromUserId: String,
        onEvent: (String) -> Unit = {}
    ): String

    /**
     * Sends a system-initiated trigger to the RAG system and returns the response.
     * The trigger prompt reaches the LLM but is NOT stored as a visible message in the conversation.
     * Only the chatbot's response is stored.
     *
     * @param threadId The thread/conversation ID
     * @param trigger The event trigger containing the prompt and target users
     * @param onEvent Callback for real-time status updates
     * @return The RAG system's response message
     */
    suspend fun sendTrigger(
        threadId: String,
        trigger: ChatTrigger,
        onEvent: (String) -> Unit = {}
    ): String
}