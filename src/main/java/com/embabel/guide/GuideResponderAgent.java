package com.embabel.guide;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Condition;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.CoreToolGroups;
import com.embabel.agent.discord.DiscordUser;
import com.embabel.agent.identity.User;
import com.embabel.agent.rag.ContentElementSearch;
import com.embabel.guide.domain.WebUser;
import com.embabel.agent.rag.EntitySearch;
import com.embabel.agent.rag.HyDE;
import com.embabel.agent.rag.tools.DualShotConfig;
import com.embabel.agent.rag.tools.RagReference;
import com.embabel.chat.AssistantMessage;
import com.embabel.chat.Chatbot;
import com.embabel.chat.Conversation;
import com.embabel.chat.UserMessage;
import com.embabel.chat.agent.AgentProcessChatbot;
import com.embabel.chat.agent.ConversationContinues;
import com.embabel.chat.agent.ConversationOver;
import com.embabel.chat.agent.ConversationStatus;
import com.embabel.guide.domain.GuideUser;
import com.embabel.guide.domain.GuideUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.HashMap;
import java.util.Set;

/**
 * Core chatbot agent
 */
@Agent(description = "Embabel developer guide bot agent",
        name = GuideResponderAgent.NAME)
public class GuideResponderAgent {

    private final GuideData guideData;
    private final GuideUserRepository guideUserRepository;

    private final Logger logger = LoggerFactory.getLogger(GuideResponderAgent.class);
    private final GuideConfig guideConfig;

    public GuideResponderAgent(GuideData guideData, GuideUserRepository guideUserRepository, GuideConfig guideConfig) {
        this.guideData = guideData;
        this.guideUserRepository = guideUserRepository;
        this.guideConfig = guideConfig;
    }

    static final String NAME = "GuideAgent";

    static final String LAST_EVENT_WAS_USER_MESSAGE = "user_last";

    @Condition(name = LAST_EVENT_WAS_USER_MESSAGE)
    public boolean lastEventWasUserMessage(OperationContext context) {
        return context.lastResult() instanceof UserMessage;
    }

    private GuideUser getGuideUser(@Nullable User user) {
        switch (user) {
            case null -> {
                logger.warn("user is null: Cannot create or fetch GuideUser");
                return null;
            }
            case GuideUser gu -> {
                return gu;
            }
            case DiscordUser du -> {
                return guideUserRepository.findByDiscordUserId(du.getId())
                        .orElseGet(() -> {
                            var newUser = GuideUser.createFromDiscord(du);
                            logger.info("Created new Discord user: {}", newUser);
                            return guideUserRepository.save(newUser);
                        });
            }
            case WebUser wu -> {
                return wu.getGuideUser();
            }
            default -> {
                var newUser = new GuideUser();
                logger.info("Created new guide user: {}", newUser);
                return guideUserRepository.save(newUser);
            }
        }
    }

    @Action(canRerun = true,
            pre = {LAST_EVENT_WAS_USER_MESSAGE})
    ConversationStatus respond(
            Conversation conversation,
            ActionContext context) {
        logger.info("Incoming request from user {}", context.user());
        var guideUser = getGuideUser(context.user());

        var persona = guideUser != null && guideUser.persona() != null ? guideUser.persona() : guideConfig.defaultPersona();
        var templateModel = new HashMap<String, Object>();
        if (guideUser != null) {
          templateModel.put("guideUser", guideUser);
        }
        templateModel.put("persona", persona);
        var assistantMessage = context
                .ai()
                .withLlm(guideData.config().llm())
                .withReferences(guideData.referencesForUser(context.user()))
                .withTools(CoreToolGroups.WEB)
                .withReference(new RagReference("", "",
                        guideData
                                .ragOptions()
                                .withHyDE(new HyDE("The Embabel Agent Framework", 40))
                                .withContentElementSearch(ContentElementSearch.CHUNKS_ONLY)
                                .withEntitySearch(new EntitySearch(Set.of(
                                        "Concept", "Example"
                                ), false))
                                .withDesiredMaxLatency(Duration.ofMinutes(10))
                                .withDualShot(new DualShotConfig(100)),
//                                .withListener(e -> {
//                                    if (e instanceof RagPipelineEvent rpe) {
//                                        context.updateProgress(rpe.getDescription());
//                                    }
//                                }),
                        context.ai().withLlmByRole("summarizer")))
                .withId("chat_response")
                .withTemplate("guide_system")
                .respondWithSystemPrompt(conversation, templateModel);
        conversation.addMessage(assistantMessage);
        context.sendMessage(assistantMessage);
        return ConversationContinues.with(assistantMessage);
    }

    @AchievesGoal(description = "Conversation completed")
    @Action
    ConversationOver respondAndTerminate(
            ConversationOver conversationOver,
            Conversation conversation,
            ActionContext context) {
        context.sendMessage(new AssistantMessage("Conversation over: " + conversationOver.getReason()));
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
