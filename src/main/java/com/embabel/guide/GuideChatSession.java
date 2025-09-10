package com.embabel.guide;

import com.embabel.agent.api.common.AiBuilder;
import com.embabel.agent.channel.OutputChannel;
import com.embabel.agent.identity.User;
import com.embabel.agent.rag.pipeline.event.RagPipelineEvent;
import com.embabel.chat.AssistantMessage;
import com.embabel.chat.ChatSession;
import com.embabel.chat.Conversation;
import com.embabel.chat.UserMessage;
import com.embabel.chat.support.InMemoryConversation;
import org.jetbrains.annotations.NotNull;
import org.springframework.lang.Nullable;

import java.util.Collections;

/**
 * GuideLoader will have loaded content
 */
public class GuideChatSession implements ChatSession {

    // TODO could have memories

    private final AiBuilder aiBuilder;
    private final GuideData guideData;
    @Nullable
    private final User user;

    private final OutputChannel outputChannel;

    private final Conversation conversation = new InMemoryConversation();

    public GuideChatSession(AiBuilder aiBuilder,
                            GuideData guideData,
                            @Nullable User user,
                            @NotNull OutputChannel outputChannel
    ) {
        this.guideData = guideData;
        this.aiBuilder = aiBuilder;
        this.user = user;
        this.outputChannel = outputChannel;
    }

    @NotNull
    @Override
    public OutputChannel getOutputChannel() {
        return outputChannel;
    }

    @Nullable
    @Override
    public User getUser() {
        return user;
    }

    @NotNull
    @Override
    public Conversation getConversation() {
        return conversation;
    }

    @Override
    public void onUserMessage(@NotNull UserMessage userMessage) {
        conversation.addMessage(userMessage);
        final var assistantMessage = aiBuilder
                .withShowPrompts(false)
                .ai()
                .withLlm(guideData.guideConfig().llm())
                .withReferences(guideData.references())
                .withRag(guideData.ragOptions().withListener(e -> {
                    if (e instanceof RagPipelineEvent rpe) {
                        var am = new AssistantMessage(rpe.getDescription());
//                        messageListener.onMessage(am, conversation);
                    }
                }))
                .withTemplate("guide_system")
                .respondWithSystemPrompt(conversation,
                        guideData.templateModel(Collections.singletonMap("user", user)));

        saveAndSend(assistantMessage);
    }
}
