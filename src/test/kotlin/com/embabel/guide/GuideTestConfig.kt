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

import org.neo4j.ogm.config.Configuration
import org.neo4j.ogm.session.SessionFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories
import org.springframework.data.neo4j.transaction.Neo4jTransactionManager
import org.springframework.transaction.PlatformTransactionManager

/**
 * Test-specific Neo4j configuration that provides beans for integration tests.
 * This overrides the production NeoOgmConfig which is disabled during tests (@Profile("!test")).
 */
@org.springframework.context.annotation.Configuration
@EnableNeo4jRepositories(basePackages = ["com.embabel.guide.domain"])
@org.springframework.context.annotation.Profile("test")
class GuideTestConfig {

    @Bean
    fun neo4jTestContainer(): Neo4jTestContainer {
        return Neo4jTestContainer.instance
    }

    @Bean
    fun ogmConfiguration(): Configuration {
        val container = neo4jTestContainer()
        return Configuration.Builder()
            .uri(container.boltUrl)
            .credentials("neo4j", container.adminPassword)
            .build()
    }

    @Bean
    @Primary
    fun sessionFactory(): SessionFactory {
        return SessionFactory(
            ogmConfiguration(),
            "com.embabel.guide.domain"
        )
    }

    @Bean
    @Primary
    fun transactionManager(): PlatformTransactionManager {
        return Neo4jTransactionManager(sessionFactory())
    }
}
