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
import com.embabel.guide.domain.DrivineGuideUserRepository;
import com.embabel.guide.domain.GuideUserWithDiscordUserInfo;
import com.embabel.guide.domain.GuideUserWithWebUser;
import com.embabel.guide.domain.HasGuideUserData;
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
    private final DrivineGuideUserRepository guideUserRepository;

    private final Logger logger = LoggerFactory.getLogger(ChatActions.class);
    private final GuideProperties guideProperties;
    private final DrivineStore drivineStore;

    public ChatActions(
            DataManager dataManager,
            DrivineGuideUserRepository guideUserRepository,
            DrivineStore drivineStore,
            GuideProperties guideProperties) {
        this.dataManager = dataManager;
        this.guideUserRepository = guideUserRepository;
        this.guideProperties = guideProperties;
        this.drivineStore = drivineStore;
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

    @Action(canRerun = true, trigger = UserMessage.class)
    void respond(
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

