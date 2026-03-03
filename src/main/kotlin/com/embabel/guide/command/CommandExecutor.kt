package com.embabel.guide.command

import com.embabel.guide.chat.model.CommandRequest
import com.embabel.guide.chat.model.CommandResponse
import com.embabel.guide.chat.service.ChatService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Handles frontend websocket round-trips for commands that need the browser to execute
 * (voice changes, audio effects, etc.).
 */
@Service
class CommandExecutor(
    private val chatService: ChatService,
) {

    private val logger = LoggerFactory.getLogger(CommandExecutor::class.java)

    private val pendingCommands = ConcurrentHashMap<String, CompletableFuture<CommandResponse>>()

    fun executePersonaChange(persona: String, webUserId: String?): String {
        if (webUserId == null) return "Persona change is only available for web users."
        return sendAndWait(webUserId, CommandRequest(
            correlationId = UUID.randomUUID().toString(),
            type = "change_persona",
            value = persona,
        ))
    }

    fun executeVoiceChange(voice: String, webUserId: String?): String {
        if (webUserId == null) return "Voice change is only available for web users."
        return sendAndWait(webUserId, CommandRequest(
            correlationId = UUID.randomUUID().toString(),
            type = "change_voice",
            value = voice,
        ))
    }

    fun executeEffects(effects: String, clearPrevious: Boolean, webUserId: String?): String {
        if (webUserId == null) return "Audio effects are only available for web users."
        return sendAndWait(webUserId, CommandRequest(
            correlationId = UUID.randomUUID().toString(),
            type = "apply_effects",
            value = effects,
            clearPrevious = clearPrevious,
        ))
    }

    fun completeCommand(response: CommandResponse) {
        val future = pendingCommands.remove(response.correlationId)
        if (future != null) {
            future.complete(response)
        } else {
            logger.warn("Received command response for unknown correlationId: {}", response.correlationId)
        }
    }

    private fun sendAndWait(webUserId: String, request: CommandRequest): String {
        val future = CompletableFuture<CommandResponse>()
        pendingCommands[request.correlationId] = future

        logger.info("Sending {} command to user {}, correlationId={}", request.type, webUserId, request.correlationId)
        chatService.sendCommandToUser(webUserId, request)

        return try {
            val response = future.get(5, TimeUnit.SECONDS)
            if (response.success) response.message else "Failed: ${response.message}"
        } catch (e: TimeoutException) {
            pendingCommands.remove(request.correlationId)
            logger.warn("Command {} timed out for user {}", request.correlationId, webUserId)
            "Command timed out waiting for browser response."
        } catch (e: Exception) {
            pendingCommands.remove(request.correlationId)
            logger.error("Command {} failed for user {}: {}", request.correlationId, webUserId, e.message, e)
            "Command failed: ${e.message}"
        }
    }
}
