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

import com.embabel.agent.spi.LlmService
import com.embabel.agent.spi.support.springai.SpringAiLlmService
import com.embabel.common.ai.model.DefaultOptionsConverter
import com.embabel.common.ai.model.EmbeddingService
import com.embabel.common.ai.model.SpringAiEmbeddingService
import com.embabel.hub.WelcomeGreeter
import org.mockito.Mockito.mock
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.document.Document
import org.springframework.ai.embedding.Embedding
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import java.util.LinkedList
import kotlin.random.Random

/**
 * Test-specific configuration that provides fake AI beans for integration tests.
 * This allows tests to run without requiring actual API keys.
 */
@Configuration
@Profile("test")
class GuideTestConfig {

    /**
     * Test LLM bean that matches the default-llm configuration
     */
    @Bean(name = ["test-llm"])
    fun testLlm(): LlmService<*> = SpringAiLlmService(
        name = "test-llm",
        chatModel = mock(ChatModel::class.java),
        provider = "test",
        optionsConverter = DefaultOptionsConverter
    )

    /**
     * Test embedding service that matches the default-embedding-model configuration
     */
    @Bean(name = ["test"])
    @Primary
    fun testEmbeddingService(): EmbeddingService = SpringAiEmbeddingService(
        name = "test",
        model = FakeEmbeddingModel(),
        provider = "test"
    )

    /**
     * No-op WelcomeGreeter for tests to avoid fire-and-forget coroutines
     * that can interfere with transactional test rollback.
     */
    @Bean
    @Primary
    fun testWelcomeGreeter(): WelcomeGreeter {
        return object : WelcomeGreeter {
            override fun greetNewUser(guideUserId: String, webUserId: String, displayName: String) {
                // No-op for tests
            }
        }
    }
}

/**
 * A fake EmbeddingModel that returns random embeddings for testing.
 * Returns 1536-dimensional vectors (same as OpenAI text-embedding-3-small).
 */
class FakeEmbeddingModel(
    private val dimensions: Int = 1536,
) : EmbeddingModel {

    override fun embed(document: Document): FloatArray {
        return FloatArray(dimensions) { Random.nextFloat() }
    }

    override fun embed(texts: List<String>): MutableList<FloatArray> {
        return texts.map { FloatArray(dimensions) { Random.nextFloat() } }.toMutableList()
    }

    override fun call(request: EmbeddingRequest): EmbeddingResponse {
        val output = LinkedList<Embedding>()
        for (i in request.instructions.indices) {
            output.add(Embedding(FloatArray(dimensions) { Random.nextFloat() }, i))
        }
        return EmbeddingResponse(output)
    }

    override fun dimensions(): Int = dimensions
}
