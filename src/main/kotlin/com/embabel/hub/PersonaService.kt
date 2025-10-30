package com.embabel.hub

import org.slf4j.LoggerFactory
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets

/**
 * Service for managing persona templates.
 */
@Service
class PersonaService {

    private val logger = LoggerFactory.getLogger(PersonaService::class.java)
    private val resourceResolver = PathMatchingResourcePatternResolver()

    data class Persona(
        val name: String,
        val description: String
    )

    /**
     * Lists all available persona templates from the classpath.
     *
     * @return List of personas with name and description
     */
    fun listPersonas(): List<Persona> {
        return try {
            val resources: Array<Resource> = resourceResolver.getResources("classpath:prompts/persona/*.jinja")

            resources.mapNotNull { resource ->
                try {
                    val filename = resource.filename ?: return@mapNotNull null
                    val name = filename.removeSuffix(".jinja")
                    val description = resource.inputStream.readBytes().toString(StandardCharsets.UTF_8)

                    Persona(name = name, description = description)
                } catch (e: Exception) {
                    logger.error("Error reading persona file: ${resource.filename}", e)
                    null
                }
            }.sortedBy { it.name }
        } catch (e: Exception) {
            logger.error("Error listing persona files", e)
            emptyList()
        }
    }
}