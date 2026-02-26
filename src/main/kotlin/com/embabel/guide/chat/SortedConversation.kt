package com.embabel.guide.chat

import com.embabel.chat.Conversation
import com.embabel.chat.Message

/**
 * Wrapper that returns messages sorted by timestamp.
 * Needed because async persistence can cause message ordering issues
 * in the underlying Conversation implementation.
 *
 * Delegates all mutation (addMessage, etc.) to the original conversation.
 */
class SortedConversation(private val delegate: Conversation) : Conversation by delegate {

    override val messages: List<Message>
        get() = delegate.messages.sortedBy { it.timestamp }
}
