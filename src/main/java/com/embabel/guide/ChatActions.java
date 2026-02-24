package com.embabel.guide;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.EmbabelComponent;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.identity.User;
import com.embabel.agent.discord.DiscordUser;
import com.embabel.agent.rag.neo.drivine.DrivineStore;
import com.embabel.agent.rag.tools.ToolishRag;
import com.embabel.agent.rag.tools.TryHyDE;
import com.embabel.chat.AssistantMessage;
import com.embabel.chat.Conversation;
import com.embabel.chat.ChatTrigger;
import com.embabel.chat.UserMessage;
import com.embabel.guide.domain.DiscordUserInfoData;
import com.embabel.guide.domain.GuideUser;
import com.embabel.guide.domain.GuideUserData;
import com.embabel.guide.domain.GuideUserRepository;
import com.embabel.guide.rag.DataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import com.embabel.agent.api.common.PromptRunner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Actions to respond to user messages and system-initiated triggers in the Guide application.
 */
@EmbabelComponent
public class ChatActions {

    private final DataManager dataManager;
    private final GuideUserRepository guideUserRepository;

    private final Logger logger = LoggerFactory.getLogger(ChatActions.class);
    private final GuideProperties guideProperties;
    private final DrivineStore drivineStore;

    public ChatActions(
            DataManager dataManager,
            GuideUserRepository guideUserRepository,
            DrivineStore drivineStore,
            GuideProperties guideProperties) {
        this.dataManager = dataManager;
        this.guideUserRepository = guideUserRepository;
        this.guideProperties = guideProperties;
        this.drivineStore = drivineStore;
    }

    private GuideUser getGuideUser(@Nullable User user) {
        switch (user) {
            case null -> {
                logger.warn("user is null: Cannot create or fetch GuideUser");
                return null;
            }
            case DiscordUser du -> {
                return guideUserRepository.findByDiscordUserId(du.getId())
                        .orElseGet(() -> {
                            var discordInfo = du.getDiscordUser();
                            var displayName = discordInfo.getDisplayName() != null
                                    ? discordInfo.getDisplayName()
                                    : discordInfo.getUsername();
                            var guideUserData = new GuideUserData(
                                    java.util.UUID.randomUUID().toString(),
                                    displayName != null ? displayName : "",
                                    discordInfo.getUsername() != null ? discordInfo.getUsername() : "",
                                    null,  // email
                                    null,  // persona
                                    null   // customPrompt
                            );
                            var discordUserInfo = new DiscordUserInfoData(
                                    discordInfo.getId(),
                                    discordInfo.getUsername(),
                                    discordInfo.getDiscriminator(),
                                    discordInfo.getDisplayName(),
                                    discordInfo.isBot(),
                                    discordInfo.getAvatarUrl()
                            );
                            var created = guideUserRepository.createWithDiscord(guideUserData, discordUserInfo);
                            logger.info("Created new Discord user: {}", created);
                            return created;
                        });
            }
            case GuideUser gu -> {
                // Already a GuideUser, look it up by ID to ensure we have latest data
                if (gu.getWebUser() != null) {
                    return guideUserRepository.findByWebUserId(gu.getWebUser().getId())
                            .orElseThrow(() -> new RuntimeException("Missing user with id: " + gu.getWebUser().getId()));
                } else if (gu.getDiscordUserInfo() != null) {
                    return guideUserRepository.findByDiscordUserId(gu.getDiscordUserInfo().getId())
                            .orElseThrow(() -> new RuntimeException("Missing user with id: " + gu.getDiscordUserInfo().getId()));
                } else {
                    return gu;
                }
            }
            default -> {
                throw new RuntimeException("Unknown user type: " + user);
            }
        }
    }

    private PromptRunner.Rendering buildRendering(ActionContext context) {
        return context
                .ai()
                .withLlm(guideProperties.chatLlm())
                .withId("chat_response")
                .withReferences(dataManager.referencesForUser(context.user()))
                .withToolGroups(guideProperties.toolGroups())
                .withReference(new ToolishRag(
                                "docs",
                                "Embabel docs",
                                drivineStore
                        ).withHint(TryHyDE.usingConversationContext())
                )
                .withTemplate("guide_system");
    }

    private Map<String, Object> buildTemplateModel(GuideUser guideUser) {
        var persona = guideUser.getCore().getPersona() != null
                ? guideUser.getCore().getPersona()
                : guideProperties.defaultPersona();
        var templateModel = new HashMap<String, Object>();
        templateModel.put("persona", persona);

        var userMap = new HashMap<String, Object>();
        var displayName = guideUser.getDisplayName();
        if (!"Unknown".equals(displayName)) {
            userMap.put("displayName", displayName);
        }
        userMap.put("customPersona", guideUser.getCore().getCustomPrompt());
        templateModel.put("user", userMap);
        return templateModel;
    }

    private void sendResponse(AssistantMessage assistantMessage, Conversation conversation, ActionContext context) {
        conversation.addMessage(assistantMessage);
        context.sendMessage(assistantMessage);
    }

    private void sendErrorResponse(Conversation conversation, ActionContext context) {
        var errorMessage = new AssistantMessage(
                "I'm sorry, I'm having trouble connecting to the AI service right now. Please try again in a moment.");
        sendResponse(errorMessage, conversation, context);
    }

    @Action(canRerun = true, trigger = UserMessage.class)
    void respond(
            Conversation conversation,
            ActionContext context) {
        logger.info("[TRACE] ChatActions.respond: user={}, conversationId={}", context.user(), conversation.getId());
        var messages = conversation.getMessages();
        logger.info("[TRACE] Conversation has {} messages", messages.size());
        for (int i = 0; i < messages.size(); i++) {
            var msg = messages.get(i);
            logger.info("[TRACE]   msg[{}]: role={}, content='{}'", i, msg.getRole(), msg.getContent().length() > 80 ? msg.getContent().substring(0, 80) + "..." : msg.getContent());
        }
        logger.info("[TRACE] lastResult type={}, value={}", context.lastResult() != null ? context.lastResult().getClass().getSimpleName() : "null", context.lastResult());
        var guideUser = getGuideUser(context.user());
        if (guideUser == null) {
            logger.error("Cannot respond: guideUser is null for context user {}", context.user());
            return;
        }
        try {
            var snapshot = List.copyOf(conversation.getMessages());
            var lastMsg = snapshot.isEmpty() ? null : snapshot.getLast();
            logger.info("[TRACE] User turn: {} with {} conversation messages, last: role={}, content='{}'",
                context.user(), snapshot.size(),
                lastMsg != null ? lastMsg.getRole() : "none",
                lastMsg != null ? (lastMsg.getContent().length() > 100 ? lastMsg.getContent().substring(0, 100) + "..." : lastMsg.getContent()) : "none");
            var assistantMessage = buildRendering(context)
                    .respondWithSystemPrompt(conversation, buildTemplateModel(guideUser));
            logger.info("[TRACE] LLM response: '{}'", assistantMessage.getContent().length() > 100 ? assistantMessage.getContent().substring(0, 100) + "..." : assistantMessage.getContent());
            sendResponse(assistantMessage, conversation, context);
        } catch (Exception e) {
            logger.error("LLM call failed for user {}: {}", context.user(), e.getMessage(), e);
            sendErrorResponse(conversation, context);
        }
    }

    @Action(canRerun = true, trigger = ChatTrigger.class)
    void respondToTrigger(
            Conversation conversation,
            ActionContext context) {
        var trigger = (ChatTrigger) context.lastResult();
        var user = trigger != null ? trigger.getOnBehalfOf().stream().findFirst().orElse(null) : null;
        logger.info("Incoming trigger for user {}", user);
        var guideUser = getGuideUser(user != null ? user : context.user());
        if (guideUser == null) {
            logger.error("Cannot respond to trigger: guideUser is null");
            return;
        }
        try {
            var assistantMessage = buildRendering(context)
                    .respondWithTrigger(conversation, trigger.getPrompt(), buildTemplateModel(guideUser));
            sendResponse(assistantMessage, conversation, context);
        } catch (Exception e) {
            logger.error("Trigger LLM call failed: {}", e.getMessage(), e);
            sendErrorResponse(conversation, context);
        }
    }

}

