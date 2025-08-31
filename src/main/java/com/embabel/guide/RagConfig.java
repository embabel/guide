package com.embabel.guide;

import com.embabel.agent.rag.RagService;
import com.embabel.agent.rag.lucene.LuceneRagService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RagConfig {

    @Bean
    RagService ragService() {
        return new LuceneRagService(
                "docs",
                "data/index.md"
        );
    }
}
