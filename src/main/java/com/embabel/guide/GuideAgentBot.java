package com.embabel.guide;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Condition;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.common.SomeOf;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.rag.pipeline.event.RagPipelineEvent;
import com.embabel.chat.AssistantMessage;
import com.embabel.chat.Chatbot;
import com.embabel.chat.Conversation;
import com.embabel.chat.UserMessage;
import com.embabel.chat.agent.AgentProcessChatbot;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

import javax.validation.constraints.Null;
import java.util.Collections;

record ConversationOver(String why) {
}

// TODO should go into common
record ChatbotReturn(
        @Nullable AssistantMessage assistantMessage,
        @Null ConversationOver termination
) implements SomeOf {
}


@Agent(description = "Embabel developer guide bot agent",
        name = "GuideAgent")
public record GuideAgentBot(
        GuideData guideData
) {

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
                .withLlm(guideData.guideConfig().llm())
                .withReferences(guideData.references())
                .withRag(guideData.ragOptions().withListener(e -> {
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
    ConversationOver respondAndMaybeTerminate(
            ConversationOver conversationOver,
            Conversation conversation,
            ActionContext context) {
        context.sendMessage(new AssistantMessage("Conversation over: " + conversationOver.why()));
        return conversationOver;
    }

}

@Configuration
class GuideAgentBotConfig {

    // Agents aren't wired up yet
    @Bean
    Chatbot chatbot(AgentPlatform agentPlatform) {
        return AgentProcessChatbot.withAgentByName(
                agentPlatform,
                "GuideAgent");
    }
}