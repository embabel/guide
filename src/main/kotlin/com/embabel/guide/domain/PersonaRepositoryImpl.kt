package com.embabel.guide.domain

import com.embabel.guide.domain.PersonaRepository.Companion.SYSTEM_OWNER_ID
import org.drivine.manager.GraphObjectManager
import org.drivine.manager.delete
import org.drivine.manager.load
import org.drivine.query.dsl.anyOf
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class PersonaRepositoryImpl(
    @param:Qualifier("neoGraphObjectManager") private val graphObjectManager: GraphObjectManager
) : PersonaRepository {

    @Transactional(readOnly = true)
    override fun findById(id: String): PersonaView? =
        graphObjectManager.load<PersonaView>(id)

    @Transactional(readOnly = true)
    override fun findByOwner(ownerId: String): List<PersonaView> =
        graphObjectManager.loadAll<PersonaView> {
            where { owner.id eq ownerId }
        }

    @Transactional(readOnly = true)
    override fun findForUser(userId: String): List<PersonaView> {
        val results = if (userId == SYSTEM_OWNER_ID) {
            graphObjectManager.loadAll<PersonaView> {
                where { owner.id eq SYSTEM_OWNER_ID }
            }
        } else {
            graphObjectManager.loadAll<PersonaView> {
                where {
                    anyOf {
                        owner.id eq SYSTEM_OWNER_ID
                        owner.id eq userId
                    }
                }
            }
        }
        return results.sortedWith(compareBy({ !it.persona.isSystem }, { it.persona.name }))
    }

    @Transactional(readOnly = true)
    override fun existsByNameAndOwner(name: String, ownerId: String): Boolean =
        graphObjectManager.loadAll<PersonaView> {
            where {
                persona.name eq name
                owner.id eq ownerId
            }
        }.isNotEmpty()

    @Transactional
    override fun save(persona: PersonaView): PersonaView =
        graphObjectManager.save(persona)

    @Transactional
    override fun delete(personaId: String) {
        graphObjectManager.delete<PersonaView>(personaId)
    }
}