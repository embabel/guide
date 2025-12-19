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

import org.drivine.connection.ConnectionProperties
import org.drivine.connection.DataSourceMap
import org.drivine.connection.DatabaseType
import org.drivine.connection.PropertyProvidedDataSourceMap
import org.drivine.manager.PersistenceManager
import org.drivine.manager.PersistenceManagerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.Profile
import org.springframework.context.annotation.PropertySource
import org.springframework.core.env.MapPropertySource

/**
 * Initializer that configures Neo4j properties before Spring context starts.
 * Add to test classes with: @ContextConfiguration(initializers = [Neo4jPropertiesInitializer::class])
 */
class Neo4jPropertiesInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {

    companion object {
        // Local Neo4j connection details
        private const val LOCAL_NEO4J_URI = "bolt://localhost:7687"
        private const val LOCAL_NEO4J_USERNAME = "neo4j"
        private const val LOCAL_NEO4J_PASSWORD = "brahmsian"
    }

    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        // Check USE_LOCAL_NEO4J environment variable to determine whether to use local Neo4j
        val useLocalNeo4j = Neo4jTestContainer.useLocalNeo4j()

        val activeProfiles = applicationContext.environment.activeProfiles

        println("@@@ Neo4jPropertiesInitializer.initialize() CALLED! useLocalNeo4j=$useLocalNeo4j (from USE_LOCAL_NEO4J env var), activeProfiles=${activeProfiles.joinToString(",")} @@@")

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
            val container = Neo4jTestContainer.instance!!
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

@Configuration
@ComponentScan(basePackages = ["org.drivine", "com.embabel"])
@PropertySource("classpath:application.yml")
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableConfigurationProperties(value = [PropertyProvidedDataSourceMap::class])
class TestAppContext {

    @Bean
    @Profile("test")
    fun dataSourceMap(): DataSourceMap {
        // Use Neo4jTestContainer helper methods which check USE_LOCAL_NEO4J internally
        val neo4jProperties = ConnectionProperties(
            host = extractHost(Neo4jTestContainer.getBoltUrl()),
            port = extractPort(Neo4jTestContainer.getBoltUrl()),
            userName = Neo4jTestContainer.getUsername(),
            password = Neo4jTestContainer.getPassword(),
            type = DatabaseType.NEO4J,
            databaseName = "neo4j"
        )
        return DataSourceMap(mapOf("neo" to neo4jProperties))
    }

    private fun extractHost(boltUrl: String): String {
        return boltUrl.substringAfter("bolt://").substringBefore(":")
    }

    private fun extractPort(boltUrl: String): Int {
        val portPart = boltUrl.substringAfter("bolt://").substringAfter(":")
        return try {
            if (portPart.contains("/")) {
                portPart.substringBefore("/").toInt()
            } else {
                portPart.toIntOrNull() ?: 7687
            }
        } catch (e: Exception) {
            7687
        }
    }
}

