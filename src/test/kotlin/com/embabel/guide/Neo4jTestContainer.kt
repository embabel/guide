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

import org.testcontainers.containers.Neo4jContainer
import org.testcontainers.utility.MountableFile

class Neo4jTestContainer : Neo4jContainer<Neo4jTestContainer> {
    private constructor(imageName: String) : super(imageName)

    companion object {
        /**
         * Lazy-initialized container instance. Only starts when first accessed.
         * This allows tests using local Neo4j to avoid the overhead of starting Testcontainers.
         */
        val instance: Neo4jTestContainer by lazy {
            Neo4jTestContainer("neo4j:5.26.1-enterprise")
                .withCopyFileToContainer(
                    MountableFile.forClasspathResource("apoc-extended-5.26.0.jar"),
                    "/plugins/apoc-extended-5.26.0.jar"
                )
                .withNeo4jConfig("dbms.security.procedures.unrestricted", "apoc.*")
                .withNeo4jConfig("dbms.logs.query.enabled", "INFO")
                .withNeo4jConfig("dbms.logs.query.parameter_logging_enabled", "true")
                .withNeo4jConfig("apoc.import.file.enabled", "true")
                .withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")
                .withEnv("NEO4J_PLUGINS", "[\"apoc\",\"graph-data-science\"]")
                .withEnv(
                    "APOC_CONFIG",
                    "apoc.import.file.enabled=true,apoc.import.file.use_neo4j_config=true"
                )
                .withEnv("checks.disable", "true")
                .withEnv("NEO4J_apoc_export_file_enabled", "true")
                // Commented out: Log binding causes permission issues
                // .withFileSystemBind("../../../test-neo4j-logs", "/logs")
                .withAdminPassword("embabel$$$$")
//                .withCopyFileToContainer(
//                    MountableFile.forClasspathResource("reference-data.cypher"),
//                    "/var/lib/neo4j/import/reference-data.cypher"
//                )
                .apply {
                    // Start the container
                    start()
                }
        }
    }
}
