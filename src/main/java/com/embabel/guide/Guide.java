package com.embabel.guide;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.rag.tools.RagOptions;
import com.embabel.chat.*;
import org.jetbrains.annotations.NotNull;

/**
 * GuideLoader will have loaded content
 */
public class Guide implements ChatSession {

    private final Ai ai;

    private Conversation conversation = new InMemoryConversation();

    public Guide(Ai ai) {
        this.ai = ai;
    }

    @NotNull
    @Override
    public Conversation getConversation() {
        return conversation;
    }

    @Override
    public void respond(@NotNull UserMessage userMessage, @NotNull MessageListener messageListener) {
        conversation = conversation.withMessage(userMessage);
        final var assistantMessage = ai
                .withLlmByRole("docs")
                .withRagTools(new RagOptions().withSimilarityThreshold(.0).withTopK(8))
                .withTemplate("guide_system")
                .respondWithSystemPrompt(conversation);
        conversation = conversation.withMessage(assistantMessage);
        messageListener.onMessage(assistantMessage);
    }
}
