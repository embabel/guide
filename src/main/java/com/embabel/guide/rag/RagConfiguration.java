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
package com.embabel.guide.rag;

import com.embabel.agent.rag.ingestion.ChunkTransformer;
import com.embabel.agent.rag.ingestion.ContentChunker;
import com.embabel.agent.rag.neo.drivine.DrivineCypherSearch;
import com.embabel.agent.rag.neo.drivine.DrivineStore;
import com.embabel.agent.rag.neo.drivine.NeoRagServiceProperties;
import com.embabel.common.ai.model.EmbeddingService;
import com.embabel.common.ai.model.ModelProvider;
import com.embabel.common.ai.model.ModelSelectionCriteria;
import com.embabel.guide.GuideProperties;
import org.drivine.manager.PersistenceManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Configuration for RAG (Retrieval Augmented Generation) components.
 * Creates the DrivineStore and related beans for Neo4j-based RAG operations.
 */
@Configuration
@EnableConfigurationProperties(NeoRagServiceProperties.class)
class RagConfiguration {

    @Bean
    @Primary
    EmbeddingService embeddingService(ModelProvider modelProvider) {
        return modelProvider.getEmbeddingService(ModelSelectionCriteria.getPlatformDefault());
    }

    @Bean
    ChunkTransformer chunkTransformer() {
        return ChunkTransformer.NO_OP;
    }

    @Bean
    @Primary
    DrivineStore drivineStore(
            @Qualifier("neo") PersistenceManager persistenceManager,
            PlatformTransactionManager platformTransactionManager,
            EmbeddingService embeddingService,
            ChunkTransformer chunkTransformer,
            NeoRagServiceProperties neoRagProperties,
            GuideProperties guideProperties) {
        var chunkerConfig = guideProperties.chunkerConfig() != null
                ? guideProperties.chunkerConfig()
                : new ContentChunker.Config();
        return new DrivineStore(
                persistenceManager,
                neoRagProperties,
                chunkerConfig,
                chunkTransformer,
                embeddingService,
                platformTransactionManager,
                new DrivineCypherSearch(persistenceManager)
        );
    }
}