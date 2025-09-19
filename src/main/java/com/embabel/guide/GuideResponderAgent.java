package com.embabel.guide;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Condition;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.common.SomeOf;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.CoreToolGroups;
import com.embabel.agent.rag.ContentElementSearch;
import com.embabel.agent.rag.EntitySearch;
import com.embabel.agent.rag.HyDE;
import com.embabel.agent.rag.pipeline.event.RagPipelineEvent;
import com.embabel.agent.rag.tools.DualShotConfig;
import com.embabel.chat.AssistantMessage;
import com.embabel.chat.Chatbot;
import com.embabel.chat.Conversation;
import com.embabel.chat.UserMessage;
import com.embabel.chat.agent.AgentProcessChatbot;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

record ConversationOver(@NonNull String why) {
}

record ChatbotReturn(
        @Nullable AssistantMessage assistantMessage,
        @Nullable ConversationOver termination
) implements SomeOf {
}


@Agent(description = "Embabel developer guide bot agent",
        name = GuideResponderAgent.NAME)
public record GuideResponderAgent(
        GuideData guideData
) {

    static final String NAME = "GuideAgent";

    static final String LAST_EVENT_WAS_USER_MESSAGE = "user_last";

    @Condition(name = LAST_EVENT_WAS_USER_MESSAGE)
    public boolean lastEventWasUserMessage(OperationContext context) {
        return context.lastResult() instanceof UserMessage;
    }

    @Action(canRerun = true,
            pre = {LAST_EVENT_WAS_USER_MESSAGE})
    ChatbotReturn respond(
            UserMessage userMessage,
            Conversation conversation,
            ActionContext context) {
        var assistantMessage = context
                .ai()
                .withLlm(guideData.config().llm())
                .withReferences(guideData.referencesForUser(context.user()))
                .withTools(CoreToolGroups.WEB)
                .withRag(
                        guideData
                                .ragOptions()
                                .withHyDE(new HyDE(40))
                                .withContentElementSearch(ContentElementSearch.CHUNKS_ONLY)
                                .withEntitySearch(new EntitySearch(Set.of(
                                        "Concept", "Example"
                                ), false))
                                .withDesiredMaxLatency(Duration.ofMinutes(10))
                                .withDualShot(new DualShotConfig(100))
                                .withListener(e -> {
                                    if (e instanceof RagPipelineEvent rpe) {
                                        context.updateProgress(rpe.getDescription());
                                    }
                                }))
                .withTemplate("guide_system")
                .respondWithSystemPrompt(conversation,
                        guideData.templateModel(Collections.singletonMap("user",
                                context.getProcessContext().getProcessOptions().getIdentities().getForUser())),
                        "chat_response");
        conversation.addMessage(assistantMessage);
        context.sendMessage(assistantMessage);
        return new ChatbotReturn(assistantMessage, null);
    }

    @AchievesGoal(description = "Conversation completed")
    @Action
    ConversationOver respondAndTerminate(
            ConversationOver conversationOver,
            Conversation conversation,
            ActionContext context) {
        context.sendMessage(new AssistantMessage("Conversation over: " + conversationOver.why()));
        return conversationOver;
    }

}

/**
 * Exposes the GuideAgent as a Chatbot bean
 * so it can be picked up by Discord
 */
@Configuration
class GuideAgentBotConfig {

    @Bean
    Chatbot chatbot(AgentPlatform agentPlatform) {
        return AgentProcessChatbot.withAgentByName(
                agentPlatform,
                GuideResponderAgent.NAME);
    }
}