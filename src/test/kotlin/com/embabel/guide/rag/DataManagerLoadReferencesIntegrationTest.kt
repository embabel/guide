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
package com.embabel.guide.rag

import com.embabel.guide.Neo4jPropertiesInitializer
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource

/**
 * Integration test for DataManager.loadReferences() with guide.directories.
 * Uses the same Neo4j setup as other integration tests (local or Testcontainers via Neo4jPropertiesInitializer).
 * Verifies that when guide.directories is set, loadReferences() ingests the directory without throwing.
 */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = [Neo4jPropertiesInitializer::class])
@ImportAutoConfiguration(exclude = [McpClientAutoConfiguration::class])
@TestPropertySource(
    properties = [
        "guide.urls=",
        "guide.directories[0]=./src/test/resources/sample-repo-for-ingestion"
    ]
)
class DataManagerLoadReferencesIntegrationTest {

    @Autowired
    private lateinit var dataManager: DataManager

    @Test
    fun `loadReferences ingests configured directory and returns structured result`() {
        val result = dataManager.loadReferences()

        // Verify structured result
        assertTrue(result.failedUrls().isEmpty(), "No URLs configured so none should fail")
        assertTrue(result.failedDirectories().isEmpty(), "Directory ingestion should succeed")
        assertTrue(
            result.ingestedDirectories().isNotEmpty(),
            "Should have ingested at least one directory"
        )
        assertFalse(result.hasFailures(), "Should have no failures")
        assertTrue(result.elapsed().toMillis() >= 0, "Elapsed should be non-negative")

        // Verify data actually landed in the store
        val docCount = dataManager.getDocumentCount()
        val chunkCount = dataManager.getChunkCount()
        assertTrue(
            docCount >= 1,
            "Expected at least one document after ingesting sample-repo-for-ingestion; got documentCount=$docCount"
        )
        assertTrue(
            chunkCount >= 1,
            "Expected at least one chunk after ingesting; got chunkCount=$chunkCount"
        )
    }
}
