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
import com.embabel.chat.Message
import com.embabel.chat.ChatTrigger
import com.embabel.chat.Conversation
import com.embabel.chat.UserMessage
import com.embabel.guide.chat.model.CategoryCheck
import com.embabel.guide.chat.model.MessageCategory
import com.embabel.guide.chat.model.StatusMessage
import com.embabel.guide.chat.service.ChatService
import com.embabel.guide.chat.service.JesseService
import com.embabel.guide.command.CommandExecutor
import com.embabel.guide.command.CommandResult
import com.embabel.guide.command.CommandTools
import com.embabel.guide.domain.GuideUser
import com.embabel.guide.domain.GuideUserCache
import com.embabel.guide.domain.GuideUserRepository
import com.embabel.guide.util.toDiscordUserInfoData
import com.embabel.guide.util.toGuideUserData
import com.embabel.guide.narrator.NarrationCache
import com.embabel.guide.narrator.NarratorAgent
import com.embabel.guide.rag.DataManager
import com.embabel.guide.util.truncate
import com.embabel.hub.PersonaService
import com.embabel.hub.integrations.SetupRequiredChatModel
import com.embabel.hub.integrations.LlmRole
import com.embabel.hub.integrations.UserLlmResolver
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
    private val guideUserCache: GuideUserCache,
    private val drivineStore: DrivineStore,
    private val guideProperties: GuideProperties,
    private val narrationCache: NarrationCache,
    private val narratorAgent: NarratorAgent,
    private val chatService: ChatService,
    private val personaService: PersonaService,
    private val commandExecutor: CommandExecutor,
    private val userLlmResolver: UserLlmResolver,
) {

    private val logger = LoggerFactory.getLogger(ChatActions::class.java)

    @Action(canRerun = true, trigger = UserMessage::class)
    fun respond(conversation: Conversation, context: ActionContext) {
        logger.info("[TRACE] ChatActions.respond: user={}, conversationId={}", context.user(), conversation.id)
        val messages = conversation.messages
        logger.info("[TRACE] Conversation has {} messages", messages.size)
        for (i in messages.indices) {
            val msg = messages[i]
            logger.info("[TRACE]   msg[{}]: role={}, content='{}'", i, msg.role,
                msg.content.truncate(80))
        }
        logger.info("[TRACE] lastResult type={}, value={}",
            context.lastResult()?.javaClass?.simpleName ?: "null", context.lastResult())
        val guideUser = getGuideUser(context.user())
        if (guideUser == null) {
            logger.error("Cannot respond: guideUser is null for context user {}", context.user())
            return
        }
        if (!userLlmResolver.hasLlm(guideUser.id)) {
            sendResponse(AssistantMessage(SetupRequiredChatModel.SETUP_MESSAGE), conversation, context)
            return
        }
        try {
            val snapshot = messages.toList()
            val lastMsg = snapshot.lastOrNull()
            logger.info("[TRACE] User turn: {} with {} conversation messages, last: role={}, content='{}'",
                context.user(), snapshot.size,
                lastMsg?.role ?: "none",
                lastMsg?.content?.truncate(100) ?: "none")

            val templateModel = buildTemplateModel(guideUser, snapshot)

            // Pass 1: Classify message category (nano)
            var category = MessageCategory.INFORMATIONAL
            var quickResponse: String? = null
            if (snapshot.size > 1) {
                try {
                    val userContent = (snapshot.last() as? UserMessage)?.content ?: ""
                    val check = classifyMessage(userContent, snapshot, context, guideUser, templateModel)
                    category = check.category
                    quickResponse = check.response
                    logger.info("[CLASSIFY RESULT] input='{}' category={}",
                        userContent.truncate(), category)
                } catch (e: Exception) {
                    logger.error("[CLASSIFY] Classification FAILED, falling back to full pipeline: {}", e.message, e)
                }
            }

            when (category) {
                MessageCategory.CONVERSATIONAL -> {
                    val response = quickResponse ?: "Hey there!"
                    val assistantMessage = AssistantMessage(response)
                    computeAndCacheNarration(assistantMessage, conversation, guideUser, context)
                    sendResponse(assistantMessage, conversation, context)
                    return
                }

                MessageCategory.COMMAND -> {
                    val userContent = (snapshot.last() as? UserMessage)?.content ?: ""
                    try {
                        val commandResult = executeCommands(userContent, guideUser, context, templateModel)
                        logger.info("[COMMAND] summary='{}', ragRequest='{}'",
                            commandResult.summary.truncate(100), commandResult.ragRequest?.truncate(100))

                        // Send the command summary as a message (with narration for voice mode)
                        val summaryMessage = AssistantMessage(commandResult.summary)
                        computeAndCacheNarration(summaryMessage, conversation, guideUser, context)
                        sendResponse(summaryMessage, conversation, context)

                        // If there's a leftover informational request, fall through to RAG
                        if (commandResult.ragRequest != null) {
                            logger.info("[COMMAND] Falling through to RAG for: {}", commandResult.ragRequest)
                            // Replace the user message with the extracted rag request for the RAG pipeline
                            val ragMessage = AssistantMessage(
                                buildRendering(context, guideUser)
                                    .respondWithSystemPrompt(conversation, templateModel)
                                    .content
                            )
                            computeAndCacheNarration(ragMessage, conversation, guideUser, context)
                            sendResponse(ragMessage, conversation, context)
                        }
                    } catch (e: Exception) {
                        logger.error("[COMMAND] Command execution FAILED: {}", e.message, e)
                        // Fall through to regular RAG pipeline on command failure
                    }
                    return
                }

                MessageCategory.INFORMATIONAL -> {
                    // Fall through to RAG pipeline below
                }
            }

            val assistantMessage = buildRendering(context, guideUser)
                .respondWithSystemPrompt(conversation, templateModel)
            logger.info("[TRACE] LLM response: '{}'",
                assistantMessage.content.truncate(100))
            computeAndCacheNarration(assistantMessage, conversation, guideUser, context)
            sendResponse(assistantMessage, conversation, context)
        } catch (e: Exception) {
            logger.error("LLM call failed for user {}: {}", context.user(), e.message, e)
            sendErrorResponse(conversation, context, e)
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
        if (!userLlmResolver.hasLlm(guideUser.id)) {
            logger.info("[TRIGGER] Silently aborting trigger for user {} — no LLM configured", guideUser.id)
            return
        }
        try {
            val assistantMessage = buildRendering(context, guideUser)
                .respondWithTrigger(conversation, trigger.prompt, buildTemplateModel(guideUser, conversation.messages))
            computeAndCacheNarration(assistantMessage, conversation, guideUser, context)
            sendResponse(assistantMessage, conversation, context)
        } catch (e: Exception) {
            logger.error("Trigger LLM call failed: {}", e.message, e)
            sendErrorResponse(conversation, context)
        }
    }

    private fun getGuideUser(user: User?): GuideUser? = when (user) {
        null -> {
            logger.warn("user is null: Cannot create or fetch GuideUser")
            null
        }
        is DiscordUser -> {
            val cacheKey = "discord:${user.id}"
            guideUserCache.get(cacheKey) ?: guideUserRepository.findByDiscordUserId(user.id)
                .orElseGet {
                    val created = guideUserRepository.createWithDiscord(
                        user.toGuideUserData(), user.toDiscordUserInfoData()
                    )
                    logger.info("Created new Discord user: {}", created)
                    created
                }
                .also { guideUserCache.put(cacheKey, it) }
        }
        is GuideUser -> {
            val webUserId = user.webUser?.id
            if (webUserId != null) {
                guideUserCache.get(webUserId) ?: guideUserRepository.findById(user.core.id)
                    .orElseThrow { RuntimeException("Missing GuideUser with id: ${user.core.id}") }
                    .also { guideUserCache.put(webUserId, it) }
            } else {
                guideUserRepository.findById(user.core.id)
                    .orElseThrow { RuntimeException("Missing GuideUser with id: ${user.core.id}") }
            }
        }
        else -> throw RuntimeException("Unknown user type: $user")
    }

    private fun buildRendering(context: ActionContext, guideUser: GuideUser): PromptRunner.Rendering {
        return userLlmResolver.resolve(context, guideUser.id, LlmRole.CHAT)
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
            .rendering("guide_system")
    }

    private fun buildTemplateModel(guideUser: GuideUser, messages: List<Message>): MutableMap<String, Any> {
        val persona = guideUser.core.persona ?: guideProperties.defaultPersona
        logger.info("[PERSONA] user={} persona={}", guideUser.core.id, persona)

        val userMap = mutableMapOf<String, Any?>()
        val displayName = guideUser.displayName
        if (displayName != "Unknown") {
            userMap["displayName"] = displayName
        }
        userMap["customPersona"] = guideUser.core.customPrompt

        // Greet by name on first message, ~25% of the time after that
        val isFirstMessage = messages.size <= 1
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
            val narration = narratorAgent.narrate(assistantMessage.content, persona, context, guideUser.id)
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

    private fun sendErrorResponse(conversation: Conversation, context: ActionContext, cause: Exception? = null) {
        val detail = cause?.let { userFacingErrorDetail(it) } ?: ""
        val errorMessage = AssistantMessage(
            "I'm sorry, I'm having trouble connecting to the AI service right now. $detail".trimEnd() +
                "\n\nPlease try again in a moment."
        )
        sendResponse(errorMessage, conversation, context)
    }

    private fun formatPersonaList(userId: String): String =
        personaService.listPersonasForUser(userId)
            .joinToString("\n") { p ->
                if (p.description != null) "- ${p.name}: ${p.description}" else "- ${p.name}"
            }

    companion object {
        /**
         * Extracts a user-friendly error detail from an LLM exception.
         * Safe to show — no internal details, just actionable info.
         */
        fun userFacingErrorDetail(e: Exception): String {
            val msg = e.message ?: return ""
            return when {
                msg.contains("401") || msg.contains("unauthorized", ignoreCase = true) ->
                    "Your API key appears to be invalid or expired. Please check your key in Settings."
                msg.contains("402") || msg.contains("billing", ignoreCase = true) ||
                    msg.contains("quota", ignoreCase = true) || msg.contains("insufficient", ignoreCase = true) ->
                    "Your API account may have run out of credits or exceeded its quota. Please check your billing."
                msg.contains("429") || msg.contains("rate", ignoreCase = true) ->
                    "The AI provider is rate-limiting requests. Please wait a moment."
                msg.contains("404") || msg.contains("not_found", ignoreCase = true) ->
                    "The configured AI model could not be found. Please check your settings."
                msg.contains("500") || msg.contains("502") || msg.contains("503") ->
                    "The AI provider is experiencing an outage."
                else -> ""
            }
        }
    }

    /**
     * Pass 1: Classify the latest user message into CONVERSATIONAL, COMMAND, or INFORMATIONAL using nano.
     * If conversational, includes a quick response to avoid the full RAG pipeline.
     */
    private fun classifyMessage(
        userMessage: String,
        messages: List<Message>,
        context: ActionContext,
        guideUser: GuideUser,
        templateModel: Map<String, Any>,
    ): CategoryCheck {
        val conversationContext = buildString {
            val start = maxOf(0, messages.size - 6)
            for (i in start until messages.size) {
                val m = messages[i]
                val content = m.content.truncate(200)
                append(m.role.name.lowercase()).append(": ").append(content).append("\n")
            }
        }
        val model = mutableMapOf<String, Any>().apply {
            putAll(templateModel)
            put("conversationContext", conversationContext)
            put("userMessage", userMessage)
            put("personaList", formatPersonaList(guideUser.core.id))
        }
        return userLlmResolver.resolve(context, guideUser.id, LlmRole.CLASSIFIER)
            .rendering("classifier")
            .createObject(CategoryCheck::class.java, model)
    }

    /**
     * Pass 2: Execute commands via LLM tool calling (mini).
     * The LLM calls tools, the framework executes them, then the LLM composes a summary.
     */
    private fun executeCommands(
        userMessage: String,
        guideUser: GuideUser,
        context: ActionContext,
        templateModel: Map<String, Any>,
    ): CommandResult {
        val tools = CommandTools(
            webUserId = guideUser.webUser?.id,
            personaService = personaService,
            commandExecutor = commandExecutor,
        )
        val model = mutableMapOf<String, Any>().apply {
            putAll(templateModel)
            put("userMessage", userMessage)
            put("personaList", formatPersonaList(guideUser.core.id))
        }
        return userLlmResolver.resolve(context, guideUser.id, LlmRole.CLASSIFIER)
            .withToolObject(tools)
            .rendering("command_executor")
            .createObject(CommandResult::class.java, model)
    }

}
