package com.embabel.guide;

import com.embabel.agent.rag.lucene.LuceneRagService;
import com.embabel.common.ai.model.EmbeddingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RagConfig {

    @Bean
    LuceneRagService ragService(EmbeddingService embeddingService) {
        return new LuceneRagService(
                "docs",
                "data/index.md",
                embeddingService.getModel()
        );
    }

}
