package com.embabel.guide;

import com.embabel.agent.api.common.AiBuilder;
import com.embabel.agent.api.common.LlmReference;
import com.embabel.chat.ChatSession;
import com.embabel.chat.InMemoryChatbot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;

/**
 * Exposes Guide as a chatbot
 */
@Service
public class GuideChatbot extends InMemoryChatbot {

    private final AiBuilder aiBuilder;
    private final GuideData guideData;

    private final List<LlmReference> references = new LinkedList<>();

    public GuideChatbot(
            AiBuilder aiBuilder,
            GuideData guideData) {
        this.aiBuilder = aiBuilder;
        this.guideData = guideData;
    }

    @NotNull
    @Override
    protected ChatSession doCreateSession(@Nullable String systemMessage) {
        return new GuideChatSession(aiBuilder, guideData);
    }

}
