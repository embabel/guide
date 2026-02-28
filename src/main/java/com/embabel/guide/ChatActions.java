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
import com.embabel.guide.chat.model.StatusMessage;
import com.embabel.guide.chat.model.ConversationalCheck;
import com.embabel.guide.chat.service.ChatService;
import com.embabel.guide.chat.service.JesseService;
import com.embabel.guide.narrator.NarrationCache;
import com.embabel.guide.narrator.NarratorAgent;
import com.embabel.guide.rag.DataManager;
import com.embabel.agent.api.common.PromptRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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
    private final NarrationCache narrationCache;
    private final NarratorAgent narratorAgent;
    private final ChatService chatService;

    public ChatActions(
            DataManager dataManager,
            GuideUserRepository guideUserRepository,
            DrivineStore drivineStore,
            GuideProperties guideProperties,
            NarrationCache narrationCache,
            NarratorAgent narratorAgent,
            ChatService chatService) {
        this.dataManager = dataManager;
        this.guideUserRepository = guideUserRepository;
        this.guideProperties = guideProperties;
        this.drivineStore = drivineStore;
        this.narrationCache = narrationCache;
        this.narratorAgent = narratorAgent;
        this.chatService = chatService;
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
                                    UUID.randomUUID().toString(),
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
                // Always re-read from DB by core ID to pick up persona/settings changes
                return guideUserRepository.findById(gu.getCore().getId())
                        .orElseThrow(() -> new RuntimeException("Missing GuideUser with id: " + gu.getCore().getId()));
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

    private Map<String, Object> buildTemplateModel(GuideUser guideUser, Conversation conversation) {
        var persona = guideUser.getCore().getPersona() != null
                ? guideUser.getCore().getPersona()
                : guideProperties.defaultPersona();
        logger.info("[PERSONA] user={} persona={}", guideUser.getCore().getId(), persona);
        var templateModel = new HashMap<String, Object>();
        templateModel.put("persona", persona);

        var userMap = new HashMap<String, Object>();
        var displayName = guideUser.getDisplayName();
        if (!"Unknown".equals(displayName)) {
            userMap.put("displayName", displayName);
        }
        userMap.put("customPersona", guideUser.getCore().getCustomPrompt());

        // Greet by name on first message, ~25% of the time after that
        var isFirstMessage = conversation.getMessages().size() <= 1;
        userMap.put("greetByName", isFirstMessage || ThreadLocalRandom.current().nextInt(4) == 0);

        templateModel.put("user", userMap);
        return templateModel;
    }

    private void computeAndCacheNarration(AssistantMessage assistantMessage, Conversation conversation, GuideUser guideUser, ActionContext context) {
        var conversationId = conversation.getId();
        logger.info("[NARRATION] Starting narration for conversation {}, content length={}", conversationId, assistantMessage.getContent().length());
        var webUserId = guideUser.getWebUser() != null ? guideUser.getWebUser().getId() : null;
        if (webUserId != null) {
            chatService.sendStatusToUser(webUserId, new StatusMessage(
                    UUID.randomUUID().toString(),
                    JesseService.JESSE_USER_ID,
                    "Narrating...",
                    Instant.now()));
        }
        try {
            var persona = guideUser.getCore().getPersona() != null
                    ? guideUser.getCore().getPersona()
                    : guideProperties.defaultPersona();
            var narration = narratorAgent.narrate(assistantMessage.getContent(), persona, context);
            logger.info("[NARRATION] Narration complete for conversation {}: {} chars", conversationId, narration.getText().length());
            narrationCache.put(conversationId, narration.getText());
        } catch (Exception e) {
            logger.error("[NARRATION] Narration failed for conversation {}: {}", conversationId, e.getMessage(), e);
        } finally {
            // Clear the "Narrating..." status. The ADDED event listener also tries to clear,
            // but its clear depends on fromUserId being non-null (which fails for the trigger
            // path where agent is null on the loaded conversation).
            if (webUserId != null) {
                chatService.sendStatusToUser(webUserId, new StatusMessage(
                        UUID.randomUUID().toString(),
                        JesseService.JESSE_USER_ID,
                        null,
                        Instant.now()));
            }
        }
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

    /**
     * Classify the latest user message as conversational vs informational using nano.
     * If conversational, generates a quick response to avoid the full RAG pipeline.
     */
    private ConversationalCheck classifyMessage(String userMessage, Conversation conversation,
                                                 ActionContext context, Map<String, Object> templateModel) {
        var messages = conversation.getMessages();
        var sb = new StringBuilder();
        var start = Math.max(0, messages.size() - 6);
        for (int i = start; i < messages.size(); i++) {
            var m = messages.get(i);
            var content = m.getContent().length() > 200
                    ? m.getContent().substring(0, 200) + "..."
                    : m.getContent();
            sb.append(m.getRole().name().toLowerCase()).append(": ").append(content).append("\n");
        }
        var model = new HashMap<>(templateModel);
        model.put("conversationContext", sb.toString());
        model.put("userMessage", userMessage);
        return context.ai()
                .withLlm(guideProperties.fastLlm())
                .withTemplate("classifier")
                .createObject(ConversationalCheck.class, model);
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

            var templateModel = buildTemplateModel(guideUser, conversation);

            // Short-circuit for conversational messages (acknowledgments, greetings, reactions)
            if (snapshot.size() > 1) {
                try {
                    // Use the trigger's lastResult — conversation message ordering is unreliable
                    var userContent = context.lastResult() instanceof UserMessage um
                            ? um.getContent()
                            : "";
                    var check = classifyMessage(userContent, conversation, context, templateModel);
                    logger.info("[CLASSIFY RESULT] input='{}' conversational={} response='{}'",
                        userContent.length() > 120 ? userContent.substring(0, 120) + "..." : userContent,
                        check.getConversational(),
                        check.getResponse() != null ? check.getResponse().substring(0, Math.min(120, check.getResponse().length())) : "null");
                    if (check.getConversational() && check.getResponse() != null) {
                        var assistantMessage = new AssistantMessage(check.getResponse());
                        computeAndCacheNarration(assistantMessage, conversation, guideUser, context);
                        sendResponse(assistantMessage, conversation, context);
                        return;
                    }
                } catch (Exception e) {
                    logger.error("[CLASSIFY] Classification FAILED, falling back to full pipeline: {}", e.getMessage(), e);
                }
            }

            var assistantMessage = buildRendering(context)
                    .respondWithSystemPrompt(conversation, templateModel);
            logger.info("[TRACE] LLM response: '{}'", assistantMessage.getContent().length() > 100 ? assistantMessage.getContent().substring(0, 100) + "..." : assistantMessage.getContent());
            computeAndCacheNarration(assistantMessage, conversation, guideUser, context);
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
                    .respondWithTrigger(conversation, trigger.getPrompt(), buildTemplateModel(guideUser, conversation));
            computeAndCacheNarration(assistantMessage, conversation, guideUser, context);
            sendResponse(assistantMessage, conversation, context);
        } catch (Exception e) {
            logger.error("Trigger LLM call failed: {}", e.getMessage(), e);
            sendErrorResponse(conversation, context);
        }
    }

}

