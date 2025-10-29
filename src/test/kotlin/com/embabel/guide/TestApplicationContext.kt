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

import com.embabel.GuideApplication
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * Shared test configuration for all tests.
 *
 * Controls whether tests use local Neo4j or Testcontainers:
 * - Set useLocalNeo4j = true to use local Neo4j at localhost:7687
 * - Set useLocalNeo4j = false to use Testcontainers (slower, but isolated)
 *
 * Imports GuideApplication to enable full component scanning.
 */
@TestConfiguration
@Import(GuideApplication::class)
class TestApplicationContext {

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
        private const val LOCAL_NEO4J_PASSWORD = "h4ckM3\$\$\$\$"

        @JvmStatic
        @DynamicPropertySource
        fun neo4jProperties(registry: DynamicPropertyRegistry) {
            if (useLocalNeo4j) {
                // Use local Neo4j instance
                registry.add("spring.neo4j.uri") { LOCAL_NEO4J_URI }
                registry.add("spring.neo4j.authentication.username") { LOCAL_NEO4J_USERNAME }
                registry.add("spring.neo4j.authentication.password") { LOCAL_NEO4J_PASSWORD }
            } else {
                // Use Testcontainers - only initialized when actually needed
                val container = Neo4jTestContainer.instance
                registry.add("spring.neo4j.uri") { container.boltUrl }
                registry.add("spring.neo4j.authentication.username") { "neo4j" }
                registry.add("spring.neo4j.authentication.password") { container.adminPassword }
            }
        }

        /**
         * Get the Neo4j container instance only when using Testcontainers.
         * This is lazy-loaded to avoid starting the container when using local Neo4j.
         */
        @JvmStatic
        fun getContainerIfNeeded(): Neo4jTestContainer? {
            return if (useLocalNeo4j) null else Neo4jTestContainer.instance
        }
    }
}
