package com.embabel.guide

import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.EmbabelComponent
import com.embabel.agent.api.common.ActionContext
import com.embabel.agent.api.common.PromptRunner
import com.embabel.agent.api.identity.User
import com.embabel.agent.discord.DiscordUser
import com.embabel.agent.rag.neo.drivine.DrivineStore
import com.embabel.agent.rag.tools.ToolishRag
import com.embabel.agent.rag.tools.TryHyDE
import com.embabel.chat.AssistantMessage
import com.embabel.chat.ChatTrigger
import com.embabel.chat.Conversation
import com.embabel.chat.UserMessage
import com.embabel.guide.chat.model.ConversationalCheck
import com.embabel.guide.chat.model.StatusMessage
import com.embabel.guide.chat.service.ChatService
import com.embabel.guide.chat.service.JesseService
import com.embabel.guide.domain.DiscordUserInfoData
import com.embabel.guide.domain.GuideUser
import com.embabel.guide.domain.GuideUserData
import com.embabel.guide.domain.GuideUserRepository
import com.embabel.guide.narrator.NarrationCache
import com.embabel.guide.narrator.NarratorAgent
import com.embabel.guide.rag.DataManager
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

/**
 * Actions to respond to user messages and system-initiated triggers in the Guide application.
 */
@EmbabelComponent
class ChatActions(
    private val dataManager: DataManager,
    private val guideUserRepository: GuideUserRepository,
    private val drivineStore: DrivineStore,
    private val guideProperties: GuideProperties,
    private val narrationCache: NarrationCache,
    private val narratorAgent: NarratorAgent,
    private val chatService: ChatService,
) {

    private val logger = LoggerFactory.getLogger(ChatActions::class.java)

    private fun getGuideUser(user: User?): GuideUser? {
        return when (user) {
            null -> {
                logger.warn("user is null: Cannot create or fetch GuideUser")
                null
            }
            is DiscordUser -> {
                guideUserRepository.findByDiscordUserId(user.id)
                    .orElseGet {
                        val discordInfo = user.discordUser
                        val displayName = discordInfo.displayName ?: discordInfo.username
                        val guideUserData = GuideUserData(
                            UUID.randomUUID().toString(),
                            displayName ?: "",
                            discordInfo.username ?: "",
                            null,  // email
                            null,  // persona
                            null,  // customPrompt
                        )
                        val discordUserInfo = DiscordUserInfoData(
                            discordInfo.id,
                            discordInfo.username,
                            discordInfo.discriminator,
                            discordInfo.displayName,
                            discordInfo.isBot,
                            discordInfo.avatarUrl,
                        )
                        val created = guideUserRepository.createWithDiscord(guideUserData, discordUserInfo)
                        logger.info("Created new Discord user: {}", created)
                        created
                    }
            }
            is GuideUser -> {
                // Always re-read from DB by core ID to pick up persona/settings changes
                guideUserRepository.findById(user.core.id)
                    .orElseThrow { RuntimeException("Missing GuideUser with id: ${user.core.id}") }
            }
            else -> {
                throw RuntimeException("Unknown user type: $user")
            }
        }
    }

    private fun buildRendering(context: ActionContext): PromptRunner.Rendering {
        return context
            .ai()
            .withLlm(guideProperties.chatLlm)
            .withId("chat_response")
            .withReferences(dataManager.referencesForUser(context.user()))
            .withToolGroups(guideProperties.toolGroups)
            .withReference(
                ToolishRag(
                    "docs",
                    "Embabel docs",
                    drivineStore,
                ).withHint(TryHyDE.usingConversationContext())
            )
            .withTemplate("guide_system")
    }

    private fun buildTemplateModel(guideUser: GuideUser, conversation: Conversation): MutableMap<String, Any> {
        val persona = guideUser.core.persona ?: guideProperties.defaultPersona
        logger.info("[PERSONA] user={} persona={}", guideUser.core.id, persona)

        val userMap = mutableMapOf<String, Any?>()
        val displayName = guideUser.displayName
        if (displayName != "Unknown") {
            userMap["displayName"] = displayName
        }
        userMap["customPersona"] = guideUser.core.customPrompt

        // Greet by name on first message, ~25% of the time after that
        val isFirstMessage = conversation.messages.size <= 1
        userMap["greetByName"] = isFirstMessage || ThreadLocalRandom.current().nextInt(4) == 0

        return mutableMapOf(
            "persona" to persona,
            "user" to userMap,
        )
    }

    private fun computeAndCacheNarration(
        assistantMessage: AssistantMessage,
        conversation: Conversation,
        guideUser: GuideUser,
        context: ActionContext,
    ) {
        val conversationId = conversation.id
        logger.info("[NARRATION] Starting narration for conversation {}, content length={}", conversationId, assistantMessage.content.length)
        val webUserId = guideUser.webUser?.id
        if (webUserId != null) {
            chatService.sendStatusToUser(webUserId, StatusMessage(
                UUID.randomUUID().toString(),
                JesseService.JESSE_USER_ID,
                "Narrating...",
                Instant.now(),
            ))
        }
        try {
            val persona = guideUser.core.persona ?: guideProperties.defaultPersona
            val narration = narratorAgent.narrate(assistantMessage.content, persona, context)
            logger.info("[NARRATION] Narration complete for conversation {}: {} chars", conversationId, narration.text.length)
            narrationCache.put(conversationId, narration.text)
        } catch (e: Exception) {
            logger.error("[NARRATION] Narration failed for conversation {}: {}", conversationId, e.message, e)
        } finally {
            // Clear the "Narrating..." status. The ADDED event listener also tries to clear,
            // but its clear depends on fromUserId being non-null (which fails for the trigger
            // path where agent is null on the loaded conversation).
            if (webUserId != null) {
                chatService.sendStatusToUser(webUserId, StatusMessage(
                    UUID.randomUUID().toString(),
                    JesseService.JESSE_USER_ID,
                    null,
                    Instant.now(),
                ))
            }
        }
    }

    private fun sendResponse(assistantMessage: AssistantMessage, conversation: Conversation, context: ActionContext) {
        conversation.addMessage(assistantMessage)
        context.sendMessage(assistantMessage)
    }

    private fun sendErrorResponse(conversation: Conversation, context: ActionContext) {
        val errorMessage = AssistantMessage(
            "I'm sorry, I'm having trouble connecting to the AI service right now. Please try again in a moment."
        )
        sendResponse(errorMessage, conversation, context)
    }

    /**
     * Classify the latest user message as conversational vs informational using nano.
     * If conversational, generates a quick response to avoid the full RAG pipeline.
     */
    private fun classifyMessage(
        userMessage: String,
        conversation: Conversation,
        context: ActionContext,
        templateModel: Map<String, Any>,
    ): ConversationalCheck {
        val messages = conversation.messages
        val conversationContext = buildString {
            val start = maxOf(0, messages.size - 6)
            for (i in start until messages.size) {
                val m = messages[i]
                val content = if (m.content.length > 200) m.content.substring(0, 200) + "..." else m.content
                append(m.role.name.lowercase()).append(": ").append(content).append("\n")
            }
        }
        val model = mutableMapOf<String, Any>().apply {
            putAll(templateModel)
            put("conversationContext", conversationContext)
            put("userMessage", userMessage)
        }
        return context.ai()
            .withLlm(guideProperties.classifierLlm)
            .withTemplate("classifier")
            .createObject(ConversationalCheck::class.java, model)
    }

    @Action(canRerun = true, trigger = UserMessage::class)
    fun respond(conversation: Conversation, context: ActionContext) {
        logger.info("[TRACE] ChatActions.respond: user={}, conversationId={}", context.user(), conversation.id)
        val messages = conversation.messages
        logger.info("[TRACE] Conversation has {} messages", messages.size)
        for (i in messages.indices) {
            val msg = messages[i]
            logger.info("[TRACE]   msg[{}]: role={}, content='{}'", i, msg.role,
                if (msg.content.length > 80) msg.content.substring(0, 80) + "..." else msg.content)
        }
        logger.info("[TRACE] lastResult type={}, value={}",
            context.lastResult()?.javaClass?.simpleName ?: "null", context.lastResult())
        val guideUser = getGuideUser(context.user())
        if (guideUser == null) {
            logger.error("Cannot respond: guideUser is null for context user {}", context.user())
            return
        }
        try {
            val snapshot = messages.toList()
            val lastMsg = snapshot.lastOrNull()
            logger.info("[TRACE] User turn: {} with {} conversation messages, last: role={}, content='{}'",
                context.user(), snapshot.size,
                lastMsg?.role ?: "none",
                lastMsg?.let { if (it.content.length > 100) it.content.substring(0, 100) + "..." else it.content } ?: "none")

            val templateModel = buildTemplateModel(guideUser, conversation)

            // Short-circuit for conversational messages (acknowledgments, greetings, reactions)
            if (snapshot.size > 1) {
                try {
                    // Use the trigger's lastResult — conversation message ordering is unreliable
                    val userContent = if (context.lastResult() is UserMessage) {
                        (context.lastResult() as UserMessage).content
                    } else {
                        ""
                    }
                    val check = classifyMessage(userContent, conversation, context, templateModel)
                    logger.info("[CLASSIFY RESULT] input='{}' conversational={} response='{}'",
                        if (userContent.length > 120) userContent.substring(0, 120) + "..." else userContent,
                        check.conversational,
                        check.response?.substring(0, minOf(120, check.response.length ?: 0)) ?: "null")
                    if (check.conversational && check.response != null) {
                        val assistantMessage = AssistantMessage(check.response)
                        computeAndCacheNarration(assistantMessage, conversation, guideUser, context)
                        sendResponse(assistantMessage, conversation, context)
                        return
                    }
                } catch (e: Exception) {
                    logger.error("[CLASSIFY] Classification FAILED, falling back to full pipeline: {}", e.message, e)
                }
            }

            val assistantMessage = buildRendering(context)
                .respondWithSystemPrompt(conversation, templateModel)
            logger.info("[TRACE] LLM response: '{}'",
                if (assistantMessage.content.length > 100) assistantMessage.content.substring(0, 100) + "..." else assistantMessage.content)
            computeAndCacheNarration(assistantMessage, conversation, guideUser, context)
            sendResponse(assistantMessage, conversation, context)
        } catch (e: Exception) {
            logger.error("LLM call failed for user {}: {}", context.user(), e.message, e)
            sendErrorResponse(conversation, context)
        }
    }

    @Action(canRerun = true, trigger = ChatTrigger::class)
    fun respondToTrigger(conversation: Conversation, context: ActionContext) {
        val trigger = context.lastResult() as ChatTrigger
        val user = trigger.onBehalfOf.firstOrNull()
        logger.info("Incoming trigger for user {}", user)
        val guideUser = getGuideUser(user ?: context.user())
        if (guideUser == null) {
            logger.error("Cannot respond to trigger: guideUser is null")
            return
        }
        try {
            val assistantMessage = buildRendering(context)
                .respondWithTrigger(conversation, trigger.prompt, buildTemplateModel(guideUser, conversation))
            computeAndCacheNarration(assistantMessage, conversation, guideUser, context)
            sendResponse(assistantMessage, conversation, context)
        } catch (e: Exception) {
            logger.error("Trigger LLM call failed: {}", e.message, e)
            sendErrorResponse(conversation, context)
        }
    }
}
