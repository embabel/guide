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

import com.embabel.common.ai.model.DefaultOptionsConverter
import com.embabel.common.ai.model.EmbeddingService
import com.embabel.common.ai.model.SpringEmbeddingService
import com.embabel.common.ai.model.Llm
import com.embabel.common.ai.model.PricingModel
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.document.Document
import org.springframework.ai.embedding.Embedding
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.util.*
import kotlin.random.Random

/**
 * Test-specific configuration that provides fake AI beans for integration tests.
 * This allows tests to run without requiring actual API keys.
 */
@Configuration
@Profile("test")
class GuideTestConfig {

    @Bean
    fun testLlm(): Llm {
        return Llm(
            name = "test-llm",
            model = FakeChatModel("I am a fake chat model"),
            pricingModel = PricingModel.usdPer1MTokens(.1, .1),
            provider = "test",
            optionsConverter = DefaultOptionsConverter,
        )
    }

    @Bean
    fun testEmbeddingService(): EmbeddingService {
        return SpringEmbeddingService(
            name = "test",
            model = FakeEmbeddingModel(),
            provider = "test",
        )
    }
}

/**
 * A fake ChatModel that returns a fixed response for testing.
 */
class FakeChatModel(
    private val response: String,
) : ChatModel {

    override fun call(prompt: Prompt): ChatResponse {
        return ChatResponse(listOf(Generation(AssistantMessage(response))))
    }
}

/**
 * A fake EmbeddingModel that returns random embeddings for testing.
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
}
