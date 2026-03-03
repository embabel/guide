package com.embabel.guide.command

import com.embabel.hub.PersonaService
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam

/**
 * Tool methods callable by the LLM during command execution (pass 2).
 * Created per-request with user context baked in, then registered via withToolObject().
 * All commands are executed via the frontend websocket round-trip.
 */
class CommandTools(
    private val webUserId: String?,
    private val personaService: PersonaService,
    private val commandExecutor: CommandExecutor,
) {

    private val logger = LoggerFactory.getLogger(CommandTools::class.java)

    @Tool(description = "Change the user's persona/character. Use this when the user wants to switch to a different persona.")
    fun changePersona(
        @ToolParam(description = "Name of the persona to switch to") name: String,
    ): String {
        val personas = personaService.listPersonas()
        val match = personas.find { it.name.equals(name, ignoreCase = true) }
            ?: return "Unknown persona '$name'. Available personas: ${personas.joinToString { it.name }}"

        return commandExecutor.executePersonaChange(match.name, webUserId)
    }

    @Tool(description = "Change the user's text-to-speech voice. Use this when the user wants a different voice for narration.")
    fun changeVoice(
        @ToolParam(description = "Name of the voice to use") voice: String,
    ): String {
        return commandExecutor.executeVoiceChange(voice, webUserId)
    }

    @Tool(description = "Apply audio effects to the user's narration. Use this when the user wants to add or change audio effects like echo, reverb, etc.")
    fun applyEffects(
        @ToolParam(description = "Comma-separated list of effects to apply") effects: String,
        @ToolParam(description = "Whether to clear all previous effects before applying new ones") clearPrevious: Boolean,
    ): String {
        return commandExecutor.executeEffects(effects, clearPrevious, webUserId)
    }
}
