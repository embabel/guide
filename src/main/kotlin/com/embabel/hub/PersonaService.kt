package com.embabel.hub

import com.embabel.guide.domain.GuideUserCache
import com.embabel.guide.domain.GuideUserRepository
import com.embabel.guide.domain.GuideUserService
import com.embabel.guide.domain.PersonaRepository
import com.embabel.guide.domain.PersonaRepository.Companion.SYSTEM_OWNER_ID
import com.embabel.guide.domain.PersonaView
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Facade over persona graph operations.
 * System personas are owned by bot:jesse and seeded at startup by [PersonaSeedingService].
 * User personas are created via [PersonaIngestionService] (validation + description generation).
 */
@Service
class PersonaService(
    private val personaRepository: PersonaRepository,
    private val personaIngestionService: PersonaIngestionService,
    private val guideUserRepository: GuideUserRepository,
    private val guideUserCache: GuideUserCache,
    private val personaPromptCache: PersonaPromptCache,
) {

    private val logger = LoggerFactory.getLogger(PersonaService::class.java)

    /**
     * Returns system personas plus the user's own personas, system-first then alphabetical.
     * Pass null to get system personas only (e.g. unauthenticated callers).
     */
    fun listPersonasForUser(userId: String?): List<PersonaDto> {
        val internalId = resolveInternalId(userId)
        return personaRepository.findForUser(internalId)
            .map { it.toDto(internalId) }
    }

    /**
     * Looks up the prompt text for a persona by ID. Cache-first.
     *
     * If the persona can't be resolved — e.g. it points at a retired persona that has since been
     * deleted (see the `07-retire-personas` migration) — falls back to the system "adaptive"
     * persona so chat doesn't break.
     */
    fun findPrompt(personaId: String): String? {
        personaPromptCache.get(personaId)?.let { return it }
        val prompt = personaRepository.findById(personaId)?.persona?.prompt
            ?: adaptivePrompt()
            ?: return null
        personaPromptCache.put(personaId, prompt)
        return prompt
    }

    private fun adaptivePrompt(): String? =
        personaRepository.findByNameAndOwner(GuideUserService.DEFAULT_PERSONA_NAME, SYSTEM_OWNER_ID)?.persona?.prompt

    /**
     * Resolves a web user ID to the internal GuideUser ID.
     * Cache-first to avoid DB round trips on hot paths.
     */
    private fun resolveInternalId(webUserId: String?): String {
        if (webUserId == null) return SYSTEM_OWNER_ID
        guideUserCache.get(webUserId)?.let { return it.core.id }
        return guideUserRepository.findWebUserByWebUserId(webUserId)
            .map { it.core.id }
            .orElse(SYSTEM_OWNER_ID)
    }

    @Transactional
    fun createPersona(userId: String, name: String, rawPrompt: String, voice: String?, effects: List<AudioEffect>?): PersonaDto {
        val trimmedName = name.trim()
        require(trimmedName.isNotBlank()) { "Persona name must not be blank." }
        val internalId = resolveInternalId(userId)
        if (personaRepository.existsByNameAndOwner(trimmedName, internalId)) {
            throw IllegalArgumentException("You already have a persona named '$trimmedName'.")
        }
        val result = personaIngestionService.ingest(userId, trimmedName, rawPrompt, voice, effects)
        if (result is PersonaIngestionService.IngestionResult.Failure) {
            throw IllegalArgumentException(result.reason)
        }
        val personaData = (result as PersonaIngestionService.IngestionResult.Success).personaData
        val ownerData = guideUserRepository.findWebUserByWebUserId(userId)
            .orElseThrow { NotFoundException("User not found: $userId") }
            .core
        val saved = personaRepository.save(PersonaView(persona = personaData, owner = ownerData))
        return saved.toDto(ownerData.id)
    }

    @Transactional
    fun copyPersona(userId: String, personaId: String): PersonaDto {
        val original = personaRepository.findById(personaId)
            ?: throw NotFoundException("Persona not found: $personaId")
        val internalId = resolveInternalId(userId)
        val copyName = if (personaRepository.existsByNameAndOwner(original.persona.name, internalId))
            "${original.persona.name} (copy)"
        else
            original.persona.name
        val copy = original.persona.copy(
            id = UUID.randomUUID().toString(),
            name = copyName,
            isSystem = false,
        )
        val ownerData = guideUserRepository.findWebUserByWebUserId(userId)
            .orElseThrow { NotFoundException("User not found: $userId") }
            .core
        val saved = personaRepository.save(PersonaView(persona = copy, owner = ownerData))
        return saved.toDto(ownerData.id)
    }

    @Transactional
    fun updatePersona(
        userId: String,
        personaId: String,
        name: String?,
        prompt: String?,
        voice: String?,
        effects: List<AudioEffect>?,
    ): PersonaDto {
        logger.info("[PATCH] updatePersona called: userId={}, personaId={}, name={}, prompt={}, voice={}, effects={}",
            userId, personaId, name, prompt?.take(50), voice, effects)
        val internalId = resolveInternalId(userId)
        val existing = personaRepository.findById(personaId)
            ?: throw NotFoundException("Persona not found: $personaId")
        if (existing.persona.isSystem) throw ForbiddenException("System personas cannot be edited.")
        if (existing.owner.id != internalId) throw ForbiddenException("You can only edit your own personas.")

        // Re-validate prompt through ingestion pipeline if it changed
        var newPrompt = existing.persona.prompt
        var newDescription = existing.persona.description
        require(!prompt.isNullOrBlank()) { "Persona prompt must not be empty." }
        if (prompt != existing.persona.prompt) {
            val result = personaIngestionService.revalidatePrompt(userId, prompt)
            if (result is PersonaIngestionService.IngestionResult.Failure) {
                throw IllegalArgumentException(result.reason)
            }
            val validated = (result as PersonaIngestionService.IngestionResult.Success).personaData
            newPrompt = validated.prompt
            newDescription = validated.description
        }

        val updated = existing.persona.copy(
            name = name?.trim()?.ifBlank { null } ?: existing.persona.name,
            prompt = newPrompt,
            description = newDescription,
            voice = voice ?: existing.persona.voice,
            effects = effects ?: existing.persona.effects,
        )
        logger.info("[PATCH] Changed: {}", existing.persona != updated)
        val saved = personaRepository.save(PersonaView(persona = updated, owner = existing.owner))
        personaPromptCache.invalidate(personaId)
        logger.info("[PATCH] Save complete for persona {}", saved.persona.id)
        return saved.toDto(internalId)
    }

    @Transactional
    fun deletePersona(userId: String, personaId: String) {
        val internalId = resolveInternalId(userId)
        val persona = personaRepository.findById(personaId)
            ?: throw NotFoundException("Persona not found: $personaId")
        if (persona.persona.isSystem) throw ForbiddenException("System personas cannot be deleted.")
        if (persona.owner.id != internalId) throw ForbiddenException("You can only delete your own personas.")
        personaRepository.delete(personaId)
        personaPromptCache.invalidate(personaId)
    }

    private fun PersonaView.toDto(requestingUserId: String?) = PersonaDto(
        id = persona.id,
        name = persona.name,
        description = persona.description,
        voice = persona.voice,
        effects = persona.effects,
        isSystem = persona.isSystem,
        isOwn = owner.id == requestingUserId,
    )
}