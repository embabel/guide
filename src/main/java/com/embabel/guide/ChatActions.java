package com.embabel.guide;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.EmbabelComponent;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.identity.User;
import com.embabel.agent.discord.DiscordUser;
import com.embabel.agent.rag.neo.drivine.DrivineStore;
import com.embabel.agent.rag.tools.ToolishRag;
import com.embabel.agent.rag.tools.TryHyDE;
import com.embabel.chat.Conversation;
import com.embabel.chat.UserMessage;
import com.embabel.guide.domain.DiscordUserInfoData;
import com.embabel.guide.domain.GuideUser;
import com.embabel.guide.domain.GuideUserData;
import com.embabel.guide.domain.GuideUserRepository;
import com.embabel.guide.rag.DataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import java.util.HashMap;

/**
 * Actions to respond to user messages in the Guide application
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

    @Action(canRerun = true, trigger = UserMessage.class)
    void respond(
            Conversation conversation,
            ActionContext context) {
        logger.info("Incoming request from user {}", context.user());
        // TODO null safety is a problem here
        var guideUser = getGuideUser(context.user());

        var persona = guideUser.getCore().getPersona() != null ? guideUser.getCore().getPersona() : guideProperties.defaultPersona();
        var templateModel = new HashMap<String, Object>();

        templateModel.put("persona", persona);

        // Pass user info to the template for personalization
        var userMap = new HashMap<String, Object>();
        var displayName = guideUser.getDisplayName();
        // Only include display name if it's a real name (not the "Unknown" fallback)
        if (!"Unknown".equals(displayName)) {
            userMap.put("displayName", displayName);
        }
        userMap.put("customPersona", guideUser.getCore().getCustomPrompt());
        templateModel.put("user", userMap);
        var assistantMessage = context
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
                .withTemplate("guide_system")
                .respondWithSystemPrompt(conversation, templateModel);
        conversation.addMessage(assistantMessage);
        context.sendMessage(assistantMessage);
    }

}

