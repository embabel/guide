package com.embabel.guide;

import com.embabel.agent.api.common.AiBuilder;
import com.embabel.chat.*;
import org.jetbrains.annotations.NotNull;

/**
 * GuideLoader will have loaded content
 */
public class GuideChatSession implements ChatSession {

    // TODO could have memories

    private final AiBuilder aiBuilder;
    private final GuideData guideData;

    private final Conversation conversation = new InMemoryConversation();

    public GuideChatSession(AiBuilder aiBuilder,
                            GuideData guideData
    ) {
        this.guideData = guideData;
        this.aiBuilder = aiBuilder;
    }

    @NotNull
    @Override
    public Conversation getConversation() {
        return conversation;
    }

    @Override
    public void respond(@NotNull UserMessage userMessage, @NotNull MessageListener messageListener) {
        conversation.addMessage(userMessage);
        final var assistantMessage = aiBuilder
                .withShowPrompts(true)
                .ai()
                .withLlm(guideData.guideConfig.llm())
                .withReferences(guideData.references)
                .withRag(guideData.ragOptions())
                .withTemplate("guide_system")
                .respondWithSystemPrompt(conversation, guideData.templateModel());

        conversation.addMessage(assistantMessage);
        messageListener.onMessage(assistantMessage);
    }
}
