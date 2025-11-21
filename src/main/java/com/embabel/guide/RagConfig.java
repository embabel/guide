package com.embabel.guide;

import com.embabel.agent.rag.neo.drivine.UselessTemporaryTransactionManager;
import com.embabel.agent.rag.pipeline.HyDEQueryGenerator;
import com.embabel.agent.rag.pipeline.PipelinedRagServiceEnhancer;
import com.embabel.agent.rag.service.RagService;
import com.embabel.agent.rag.service.RagServiceEnhancer;
import com.embabel.agent.rag.service.RagServiceEnhancerProperties;
import com.embabel.agent.rag.service.support.FacetedRagService;
import com.embabel.agent.rag.service.support.RagFacetProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

@Configuration
class RagConfig {

//    @Bean
//    LuceneRagService ragService(ModelProvider modelProvider) {
//        var embeddingService = modelProvider.getEmbeddingService(DefaultModelSelectionCriteria.INSTANCE);
//        LoggerFactory.getLogger(LuceneRagService.class).info(
//                "Using embedding service {} with dimensions {}",
//                embeddingService.getName(),
//                embeddingService.getModel().dimensions()
//        );
//        return new LuceneRagService(
//                "docs",
//                "Reference documentation for Embabel",
//                embeddingService.getModel()
//        );
//    }

    @Bean
    PlatformTransactionManager transactionManager() {
        return new UselessTemporaryTransactionManager();
    }

    @Bean
    RagServiceEnhancer ragServiceEnhancer(RagServiceEnhancerProperties config, HyDEQueryGenerator hyDEQueryGenerator) {
        return new PipelinedRagServiceEnhancer(config, hyDEQueryGenerator);
    }

    @Bean
    RagService ragService(List<RagFacetProvider> facetProviders) {
        return new FacetedRagService("docs", "Embabel docs", List.of(), facetProviders);
    }

}