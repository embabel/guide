/*
 * Copyright 2024-2025 Embabel Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.embabel.guide

import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.MapPropertySource

/**
 * Initializer that configures Neo4j properties before Spring context starts.
 * Add to test classes with: @ContextConfiguration(initializers = [Neo4jPropertiesInitializer::class])
 */
class Neo4jPropertiesInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {

    companion object {
        /**
         * Toggle between local Neo4j and Testcontainers.
         * Set to true for faster tests with local Neo4j (requires Neo4j running on localhost:7687).
         * Set to false to use Testcontainers (slower startup, but fully isolated).
         */
        const val useLocalNeo4j = false

        // Local Neo4j connection details
        private const val LOCAL_NEO4J_URI = "bolt://localhost:7687"
        private const val LOCAL_NEO4J_USERNAME = "neo4j"
        private const val LOCAL_NEO4J_PASSWORD = "brahmsian"
    }

    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        println("@@@ Neo4jPropertiesInitializer.initialize() CALLED! useLocalNeo4j=$useLocalNeo4j @@@")

        val properties = if (useLocalNeo4j) {
            println("@@@ Using local Neo4j at $LOCAL_NEO4J_URI @@@")
            mapOf(
                "embabel.agent.rag.neo.uri" to LOCAL_NEO4J_URI,
                "embabel.agent.rag.neo.username" to LOCAL_NEO4J_USERNAME,
                "embabel.agent.rag.neo.password" to LOCAL_NEO4J_PASSWORD,
                "spring.neo4j.uri" to LOCAL_NEO4J_URI,
                "spring.neo4j.authentication.username" to LOCAL_NEO4J_USERNAME,
                "spring.neo4j.authentication.password" to LOCAL_NEO4J_PASSWORD
            )
        } else {
            println("@@@ Using TestContainers @@@")
            val container = Neo4jTestContainer.instance
            println("@@@ TestContainer URL: ${container.boltUrl} @@@")
            mapOf(
                "embabel.agent.rag.neo.uri" to container.boltUrl,
                "embabel.agent.rag.neo.username" to "neo4j",
                "embabel.agent.rag.neo.password" to container.adminPassword,
                "spring.neo4j.uri" to container.boltUrl,
                "spring.neo4j.authentication.username" to "neo4j",
                "spring.neo4j.authentication.password" to container.adminPassword
            )
        }

        applicationContext.environment.propertySources.addFirst(
            MapPropertySource("testNeo4jProperties", properties)
        )
    }
}
