package com.embabel.guide;

import com.embabel.agent.api.common.AiBuilder;
import com.embabel.agent.api.common.LlmReference;
import com.embabel.agent.rag.tools.RagOptions;
import com.embabel.chat.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * GuideLoader will have loaded content
 */
public class Guide implements ChatSession {

    private final AiBuilder aiBuilder;
    private final List<LlmReference> references;
    private final GuideConfig guideConfig;

    private Conversation conversation = new InMemoryConversation();

    public Guide(AiBuilder aiBuilder,
                 List<LlmReference> references,
                 GuideConfig guideConfig
    ) {
        this.references = references;
        this.aiBuilder = aiBuilder;
        this.guideConfig = guideConfig;
    }


    @NotNull
    @Override
    public Conversation getConversation() {
        return conversation;
    }

    @Override
    public void respond(@NotNull UserMessage userMessage, @NotNull MessageListener messageListener) {
        conversation = conversation.withMessage(userMessage);
        final var assistantMessage = aiBuilder
                .withShowPrompts(true)
                .ai()
                .withLlmByRole("docs")
                .withReferences(references)
                .withRagTools(new RagOptions()
                        .withSimilarityThreshold(guideConfig.similarityThreshold())
                        .withTopK(guideConfig.topK()))
                .withTemplate("guide_system")
                .respondWithSystemPrompt(conversation);
        conversation = conversation.withMessage(assistantMessage);
        messageListener.onMessage(assistantMessage);
    }
}
