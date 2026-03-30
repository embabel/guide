package com.embabel.guide.domain.drivine

import com.embabel.guide.Neo4jPropertiesInitializer
import com.embabel.guide.domain.*
import com.embabel.hub.AudioEffect
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = [Neo4jPropertiesInitializer::class])
@ImportAutoConfiguration(exclude = [McpClientAutoConfiguration::class])
@Transactional
class PersonaRepositoryImplTest {

    @Autowired
    private lateinit var repository: PersonaRepositoryImpl

    @Autowired
    private lateinit var guideUserRepository: GuideUserRepositoryDefaultImpl

    private lateinit var systemOwner: GuideUserData
    private lateinit var testUser: GuideUserData

    companion object {
        const val SYSTEM_OWNER_ID = PersonaRepository.SYSTEM_OWNER_ID
    }

    @BeforeEach
    fun setUp() {
        // Ensure system owner (bot:jesse) exists
        systemOwner = guideUserRepository.findById(SYSTEM_OWNER_ID)
            .map { it.core }
            .orElseGet {
                val u = GuideUserData(id = SYSTEM_OWNER_ID, displayName = "Jesse")
                guideUserRepository.save(GuideUser(core = u))
                u
            }

        // Create a test user
        testUser = GuideUserData(id = "test-user-${UUID.randomUUID()}", displayName = "Test User")
        guideUserRepository.save(GuideUser(core = testUser))
    }

    private fun makePersonaView(
        name: String,
        owner: GuideUserData,
        isSystem: Boolean = false,
        effects: List<AudioEffect>? = null,
    ) = PersonaView(
        persona = PersonaData(
            id = UUID.randomUUID().toString(),
            name = name,
            prompt = "You speak like $name.",
            isSystem = isSystem,
            effects = effects,
        ),
        owner = owner,
    )

    @Test
    fun `save and findById round-trips persona`() {
        val view = makePersonaView("pirate", systemOwner, isSystem = true)
        val saved = repository.save(view)

        val found = repository.findById(saved.persona.id)

        assertNotNull(found)
        assertEquals("pirate", found!!.persona.name)
        assertEquals(SYSTEM_OWNER_ID, found.owner.id)
        assertTrue(found.persona.isSystem)
    }

    @Test
    fun `save and findById round-trips AudioEffect list`() {
        val view = makePersonaView("echo-test", systemOwner, effects = listOf(AudioEffect.ECHO, AudioEffect.WARM))
        val saved = repository.save(view)

        val found = repository.findById(saved.persona.id)!!

        assertEquals(listOf(AudioEffect.ECHO, AudioEffect.WARM), found.persona.effects)
    }

    @Test
    fun `findByOwner returns only personas for that owner`() {
        repository.save(makePersonaView("system-persona", systemOwner, isSystem = true))
        repository.save(makePersonaView("user-persona", testUser))

        val systemPersonas = repository.findByOwner(SYSTEM_OWNER_ID)
        val userPersonas = repository.findByOwner(testUser.id)

        assertTrue(systemPersonas.any { it.persona.name == "system-persona" })
        assertTrue(userPersonas.any { it.persona.name == "user-persona" })
        assertFalse(systemPersonas.any { it.persona.name == "user-persona" })
    }

    @Test
    fun `findForUser returns system and user personas together`() {
        repository.save(makePersonaView("system-one", systemOwner, isSystem = true))
        repository.save(makePersonaView("user-one", testUser))

        val results = repository.findForUser(testUser.id)
        val names = results.map { it.persona.name }

        assertTrue(names.contains("system-one"))
        assertTrue(names.contains("user-one"))
    }

    @Test
    fun `findForUser returns system personas first`() {
        repository.save(makePersonaView("zz-system", systemOwner, isSystem = true))
        repository.save(makePersonaView("aa-user", testUser))

        val results = repository.findForUser(testUser.id)

        // System personas come before user personas regardless of name
        val systemIdx = results.indexOfFirst { it.persona.name == "zz-system" }
        val userIdx = results.indexOfFirst { it.persona.name == "aa-user" }
        assertTrue(systemIdx < userIdx, "System persona should come before user persona")
    }

    @Test
    fun `existsByNameAndOwner returns true when persona exists`() {
        repository.save(makePersonaView("my-persona", testUser))

        assertTrue(repository.existsByNameAndOwner("my-persona", testUser.id))
        assertFalse(repository.existsByNameAndOwner("my-persona", SYSTEM_OWNER_ID))
        assertFalse(repository.existsByNameAndOwner("other-name", testUser.id))
    }

    @Test
    fun `delete removes persona from graph`() {
        val saved = repository.save(makePersonaView("to-delete", testUser))

        repository.delete(saved.persona.id)

        assertNull(repository.findById(saved.persona.id))
    }
}