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

import org.drivine.autoconfigure.EnableDrivineTestConfig
import org.drivine.test.DrivineTestContainer
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.*
import org.springframework.core.env.MapPropertySource

/**
 * Initializer that configures Neo4j properties for Embabel Agent RAG before Spring context starts.
 * Add to test classes with: @ContextConfiguration(initializers = [Neo4jPropertiesInitializer::class])
 *
 * Note: Drivine datasource is configured automatically via @EnableDrivineTestConfig.
 * This initializer only sets the embabel.agent.rag.neo properties.
 */
class Neo4jPropertiesInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {

    companion object {
        // Local Neo4j connection details
        private const val LOCAL_NEO4J_URI = "bolt://localhost:7687"
        private const val LOCAL_NEO4J_USERNAME = "neo4j"
        private const val LOCAL_NEO4J_PASSWORD = "brahmsian"
    }

    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        val useLocalNeo4j = DrivineTestContainer.useLocalNeo4j()
        val activeProfiles = applicationContext.environment.activeProfiles

        println("@@@ Neo4jPropertiesInitializer: useLocalNeo4j=$useLocalNeo4j, activeProfiles=${activeProfiles.joinToString(",")} @@@")

        val properties = if (useLocalNeo4j) {
            println("@@@ Using local Neo4j at $LOCAL_NEO4J_URI @@@")
            mapOf(
                "embabel.agent.rag.neo.uri" to LOCAL_NEO4J_URI,
                "embabel.agent.rag.neo.username" to LOCAL_NEO4J_USERNAME,
                "embabel.agent.rag.neo.password" to LOCAL_NEO4J_PASSWORD
            )
        } else {
            val boltUrl = DrivineTestContainer.getConnectionUrl()
            val password = DrivineTestContainer.getConnectionPassword()
            println("@@@ Using DrivineTestContainer at $boltUrl @@@")
            mapOf(
                "embabel.agent.rag.neo.uri" to boltUrl,
                "embabel.agent.rag.neo.username" to "neo4j",
                "embabel.agent.rag.neo.password" to password
            )
        }

        applicationContext.environment.propertySources.addFirst(
            MapPropertySource("testNeo4jProperties", properties)
        )
    }
}

@Configuration
@ComponentScan(basePackages = ["org.drivine", "com.embabel"])
@PropertySource("classpath:application.yml")
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableDrivineTestConfig
class TestAppContext