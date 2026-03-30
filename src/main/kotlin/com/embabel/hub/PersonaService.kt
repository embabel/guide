package com.embabel.hub

import com.embabel.guide.domain.GuideUserRepository
import com.embabel.guide.domain.PersonaRepository
import com.embabel.guide.domain.PersonaRepository.Companion.SYSTEM_OWNER_ID
import com.embabel.guide.domain.PersonaView
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
) {

    /**
     * Returns system personas plus the user's own personas, system-first then alphabetical.
     * Pass null to get system personas only (e.g. unauthenticated callers).
     */
    fun listPersonasForUser(userId: String?): List<PersonaDto> {
        val effectiveId = userId ?: SYSTEM_OWNER_ID
        return personaRepository.findForUser(effectiveId)
            .map { it.toDto(userId) }
    }

    @Transactional
    fun createPersona(userId: String, name: String, rawPrompt: String, voice: String?, effects: List<AudioEffect>?): PersonaDto {
        val trimmedName = name.trim()
        require(trimmedName.isNotBlank()) { "Persona name must not be blank." }
        if (personaRepository.existsByNameAndOwner(trimmedName, userId)) {
            throw IllegalArgumentException("You already have a persona named '$trimmedName'.")
        }
        val result = personaIngestionService.ingest(userId, trimmedName, rawPrompt, voice, effects)
        if (result is PersonaIngestionService.IngestionResult.Failure) {
            throw IllegalArgumentException(result.reason)
        }
        val personaData = (result as PersonaIngestionService.IngestionResult.Success).personaData
        val ownerData = guideUserRepository.findByWebUserId(userId)
            .orElseThrow { NotFoundException("User not found: $userId") }
            .core
        val saved = personaRepository.save(PersonaView(persona = personaData, owner = ownerData))
        return saved.toDto(userId)
    }

    @Transactional
    fun copyPersona(userId: String, personaId: String): PersonaDto {
        val original = personaRepository.findById(personaId)
            ?: throw NotFoundException("Persona not found: $personaId")
        val copyName = if (personaRepository.existsByNameAndOwner(original.persona.name, userId))
            "${original.persona.name} (copy)"
        else
            original.persona.name
        val copy = original.persona.copy(
            id = UUID.randomUUID().toString(),
            name = copyName,
            isSystem = false,
        )
        val ownerData = guideUserRepository.findByWebUserId(userId)
            .orElseThrow { NotFoundException("User not found: $userId") }
            .core
        val saved = personaRepository.save(PersonaView(persona = copy, owner = ownerData))
        return saved.toDto(userId)
    }

    @Transactional
    fun deletePersona(userId: String, personaId: String) {
        val persona = personaRepository.findById(personaId)
            ?: throw NotFoundException("Persona not found: $personaId")
        if (persona.persona.isSystem) throw ForbiddenException("System personas cannot be deleted.")
        if (persona.owner.id != userId) throw ForbiddenException("You can only delete your own personas.")
        personaRepository.delete(personaId)
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