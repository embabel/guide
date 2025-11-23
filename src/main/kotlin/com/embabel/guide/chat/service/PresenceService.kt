package com.embabel.guide.chat.service

import com.embabel.guide.chat.model.Presence
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Service
class PresenceService(private val messaging: SimpMessagingTemplate) {
    private val logger = LoggerFactory.getLogger(PresenceService::class.java)
    private val bySession = ConcurrentHashMap<String, Presence>()
    private val byUser = ConcurrentHashMap<String, MutableSet<String>>() // userId -> sessionIds

    fun touch(userId: String, sessionId: String, status: String?) {
        logger.debug("Updating presence for user: {} session: {} status: {}", userId, sessionId, status)
        val now = Instant.now()
        val isNewSession = !bySession.containsKey(sessionId)
        val updated = bySession.compute(sessionId) { _, existing ->
            existing?.apply {
                lastSeen = now
                status?.let { this.status = it }
            } ?: Presence(userId = userId, sessionId = sessionId, lastSeen = now, status = status ?: "active")
        }!!
        byUser.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }.add(sessionId)

        if (isNewSession) {
            logger.info("New session started for user: {} session: {}", userId, sessionId)
            logger.debug("Total active sessions: {} Total online users: {}", bySession.size, byUser.size)
        }

        // Optionally broadcast online users
        // messaging.convertAndSend("/topic/presence", onlineUsers())
    }

    fun removeSession(sessionId: String) {
        logger.debug("Removing session: {}", sessionId)
        val p = bySession.remove(sessionId) ?: run {
            logger.warn("Attempted to remove non-existent session: {}", sessionId)
            return
        }

        logger.info("Session ended for user: {} session: {}", p.userId, sessionId)
        byUser[p.userId]?.remove(sessionId)
        if (byUser[p.userId]?.isEmpty() == true) {
            byUser.remove(p.userId)
            logger.info("User {} went offline (no active sessions)", p.userId)
        }
        logger.debug("Total active sessions: {} Total online users: {}", bySession.size, byUser.size)
        // messaging.convertAndSend("/topic/presence", onlineUsers())
    }

    fun onlineUsers(): Set<String> = byUser.keys
}
