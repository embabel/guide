package com.embabel.hub

import com.embabel.guide.domain.GuideUserData
import com.embabel.guide.domain.PersonaData
import com.embabel.guide.domain.PersonaRepository
import com.embabel.guide.domain.PersonaRepository.Companion.SYSTEM_OWNER_ID
import com.embabel.guide.domain.PersonaView
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.drivine.manager.GraphObjectManager
import org.drivine.manager.load
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Seeds system personas from classpath jinja + txt files into the graph on startup,
 * owned by the bot:jesse user. Idempotent — skips any persona name that already exists.
 */
@Service
class PersonaSeedingService(
    private val personaRepository: PersonaRepository,
    @Qualifier("neoGraphObjectManager") private val graphObjectManager: GraphObjectManager,
) {

    private val logger = LoggerFactory.getLogger(PersonaSeedingService::class.java)
    private val resourceResolver = PathMatchingResourcePatternResolver()
    private val objectMapper = jacksonObjectMapper()

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class PersonaMeta(
        val description: String? = null,
        val voice: String? = null,
        val effects: List<AudioEffect>? = null,
    )

    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun seedSystemPersonas() {
        val jesseUserData = getOrCreateJesseUser()
        val existing = personaRepository.findByOwner(SYSTEM_OWNER_ID)
            .map { it.persona.name }
            .toSet()

        val resources: Array<Resource> = resourceResolver.getResources("classpath:prompts/persona/*.jinja")
        var seeded = 0
        for (resource in resources) {
            val filename = resource.filename ?: continue
            val name = filename.removeSuffix(".jinja")
            if (name in existing) continue

            val prompt = resource.inputStream.readBytes().toString(StandardCharsets.UTF_8)
            val meta = loadMeta(name)

            val personaData = PersonaData(
                id = UUID.randomUUID().toString(),
                name = name,
                prompt = prompt,
                description = meta.description,
                voice = meta.voice,
                effects = meta.effects,
                isSystem = true,
            )
            personaRepository.save(PersonaView(persona = personaData, owner = jesseUserData))
            seeded++
            logger.info("Seeded system persona: {}", name)
        }
        logger.info("Persona seeding complete — {} new, {} already existed", seeded, existing.size)
    }

    private fun getOrCreateJesseUser(): GuideUserData {
        val existing = graphObjectManager.load<GuideUserData>(SYSTEM_OWNER_ID)
        if (existing != null) return existing
        logger.info("Creating bot:jesse user during persona seeding")
        val jesse = GuideUserData(id = SYSTEM_OWNER_ID, displayName = "Jesse")
        return graphObjectManager.save(jesse)
    }

    private fun loadMeta(name: String): PersonaMeta =
        try {
            val resources = resourceResolver.getResources("classpath:prompts/persona/$name.json")
            resources.firstOrNull()?.inputStream?.let {
                objectMapper.readValue(it, PersonaMeta::class.java)
            } ?: PersonaMeta()
        } catch (e: Exception) {
            logger.warn("Could not load persona meta for '{}': {}", name, e.message)
            PersonaMeta()
        }
}