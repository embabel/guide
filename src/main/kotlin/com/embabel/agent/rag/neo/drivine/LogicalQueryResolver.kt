package com.embabel.agent.rag.neo.drivine

import com.embabel.common.util.loggerFor
import org.slf4j.Logger
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service
import java.io.IOException
import java.nio.charset.Charset

/**
 * Resolves logical query names to Cypher queries.
 */
interface LogicalQueryResolver {

    val description: String

    /**
     * Resolve a logical query name to a Cypher query.
     * Return null without error if it cannot be resolved.
     * Clients may wish to try another query to recover.
     */
    fun resolve(name: String): String?
}

const val DEFAULT_CYPHER_LOCATION = "classpath:cypher"

@Service
data class FixedLocationLogicalQueryResolver(
    val location: String = DEFAULT_CYPHER_LOCATION,
    private val resourceLoader: ResourceLoader = DefaultResourceLoader(),
) : LogicalQueryResolver {
    override val description: String = "Load from fixed location $location, expecting .cypher suffix"

    private val logger: Logger = loggerFor<FixedLocationLogicalQueryResolver>()

    override fun resolve(name: String): String? =
        try {
            logger.debug("Resource Loader in use = {}", resourceLoader::class.java.name)
            resourceLoader.getResource("$location/$name.cypher")
                .getContentAsString(Charset.defaultCharset())
                .trim()
        } catch (e: IOException) {
            logger.warn("Could not load logical query '$name' from '$location'", e)
            null
        }
}
