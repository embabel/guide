package com.embabel.guide.chat.controller

import com.embabel.guide.chat.model.ChatMessage
import com.embabel.guide.chat.model.CommandResponse
import com.embabel.guide.chat.model.MessageAck
import com.embabel.guide.chat.service.JesseService
import com.embabel.guide.chat.service.MessageDeliveryService
import com.embabel.guide.command.CommandExecutor
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import java.security.Principal

@Controller
class ChatController(
    private val jesseService: JesseService,
    private val messageDeliveryService: MessageDeliveryService,
    private val commandExecutor: CommandExecutor,
) {

    private val logger = LoggerFactory.getLogger(ChatController::class.java)

    /**
     * Receive a chat message and send it to Jesse for processing.
     * Messages are persisted to the specified session.
     */
    @MessageMapping("chat.send")
    fun receive(principal: Principal, payload: ChatMessage) {
        logger.info("ChatController received message from webUser {} in session {}: {}",
            principal.name, payload.sessionId, payload.body)
        jesseService.receiveMessage(
            sessionId = payload.sessionId,
            fromWebUserId = principal.name,
            message = payload.body
        )
    }

    /**
     * Client acknowledges receipt of a message, canceling delivery retries.
     */
    @MessageMapping("message.ack")
    fun acknowledgeMessage(principal: Principal, payload: MessageAck) {
        logger.debug("Message {} acknowledged by webUser {}", payload.messageId, principal.name)
        messageDeliveryService.acknowledge(payload.messageId)
    }

    /**
     * Receive a command result from the frontend (e.g., voice change confirmation).
     */
    @MessageMapping("command.result")
    fun receiveCommandResult(principal: Principal, payload: CommandResponse) {
        logger.info("Command result from webUser {}: correlationId={}, success={}",
            principal.name, payload.correlationId, payload.success)
        commandExecutor.completeCommand(payload)
    }
}
