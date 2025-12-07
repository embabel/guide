package com.embabel.guide;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Condition;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.common.PlannerType;
import com.embabel.agent.api.identity.User;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.CoreToolGroups;
import com.embabel.agent.discord.DiscordUser;
import com.embabel.agent.rag.neo.drivine.DrivineStore;
import com.embabel.agent.rag.tools.ToolishRag;
import com.embabel.chat.AssistantMessage;
import com.embabel.chat.Chatbot;
import com.embabel.chat.Conversation;
import com.embabel.chat.UserMessage;
import com.embabel.chat.agent.AgentProcessChatbot;
import com.embabel.chat.agent.ConversationContinues;
import com.embabel.chat.agent.ConversationOver;
import com.embabel.chat.agent.ConversationStatus;
import com.embabel.guide.domain.drivine.DrivineGuideUserRepository;
import com.embabel.guide.domain.drivine.GuideUserWithDiscordUserInfo;
import com.embabel.guide.domain.drivine.GuideUserWithWebUser;
import com.embabel.guide.domain.drivine.HasGuideUserData;
import com.embabel.guide.rag.DataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

import java.util.HashMap;

/**
 * Core chatbot agent
 */
@Agent(
        description = "Embabel developer guide bot agent",
        planner = PlannerType.UTILITY)
public class GuideResponderAgent {

    private final DataManager dataManager;
    private final DrivineGuideUserRepository guideUserRepository;

    private final Logger logger = LoggerFactory.getLogger(GuideResponderAgent.class);
    private final GuideProperties guideProperties;
    private final DrivineStore drivineStore;

    public GuideResponderAgent(
            DataManager dataManager,
            DrivineGuideUserRepository guideUserRepository,
            DrivineStore drivineStore,
            GuideProperties guideProperties) {
        this.dataManager = dataManager;
        this.guideUserRepository = guideUserRepository;
        this.guideProperties = guideProperties;
        this.drivineStore = drivineStore;
    }

    static final String LAST_EVENT_WAS_USER_MESSAGE = "user_last";

    @Condition(name = LAST_EVENT_WAS_USER_MESSAGE)
    public boolean lastEventWasUserMessage(OperationContext context) {
        return context.lastResult() instanceof UserMessage;
    }

    private HasGuideUserData getGuideUser(@Nullable User user) {
        switch (user) {
            case null -> {
                logger.warn("user is null: Cannot create or fetch GuideUser");
                return null;
            }
            case DiscordUser du -> {
                return guideUserRepository.findByDiscordUserId(du.getId())
                        .orElseGet(() -> {
                            var composed = GuideUserWithDiscordUserInfo.fromDiscordUser(du);
                            var created = guideUserRepository.createWithDiscord(
                                    composed.getGuideUserData(),
                                    composed.getDiscordUserInfo()
                            );
                            logger.info("Created new Discord user: {}", created);
                            return created;
                        });
            }
            case GuideUserWithWebUser wu -> {
                return guideUserRepository.findByWebUserId(wu.getWebUser().getId())
                        .orElseThrow(() -> new RuntimeException("Missing user with id: " + wu.getWebUser().getId()));
            }
            case HasGuideUserData gu -> {
                return gu;
            }
            default -> {
                throw new RuntimeException("Unknown user type: " + user);
            }
        }
    }

    @Action(canRerun = true,
            pre = {LAST_EVENT_WAS_USER_MESSAGE})
    ConversationStatus respond(
            Conversation conversation,
            ActionContext context) {
        logger.info("Incoming request from user {}", context.user());
        // TODO null safety is a problem here
        var guideUser = getGuideUser(context.user()).guideUserData();

        var persona = guideUser.getPersona() != null ? guideUser.getPersona() : guideProperties.defaultPersona();
        var templateModel = new HashMap<String, Object>();

        templateModel.put("persona", persona);
        var assistantMessage = context
                .ai()
                .withLlm(guideProperties.chatLlm())
                .withId("chat_response")
                .withReferences(dataManager.referencesForUser(context.user()))
                .withTools(CoreToolGroups.WEB)
                .withReference(new ToolishRag(
                        "docs",
                        "Embabel docs",
                        drivineStore
                ))
                .withTemplate("guide_system")
                .respondWithSystemPrompt(conversation, templateModel);
        conversation.addMessage(assistantMessage);
        context.sendMessage(assistantMessage);
        return ConversationContinues.with(assistantMessage);
    }

    @Action
    @AchievesGoal(description = "End the conversation politely")
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
        return AgentProcessChatbot.utilityFromPlatform(
                agentPlatform);
    }
}

@Configuration
class McpTools {

    @McpTool
    public String reverse(String thing) {
        return new StringBuilder(thing).reverse().toString();
    }
}
