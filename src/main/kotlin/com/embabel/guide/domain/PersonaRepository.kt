package com.embabel.guide.domain

interface PersonaRepository {

    companion object {
        /** The user ID that owns all system-seeded personas. */
        const val SYSTEM_OWNER_ID = "bot:jesse"
    }


    fun findById(id: String): PersonaView?
    fun findByOwner(ownerId: String): List<PersonaView>

    /**
     * Returns the union of system personas (owned by bot:jesse) and the user's own personas,
     * sorted by name with system personas first.
     */
    fun findForUser(userId: String): List<PersonaView>

    fun existsByNameAndOwner(name: String, ownerId: String): Boolean
    fun save(persona: PersonaView): PersonaView
    fun delete(personaId: String)
}