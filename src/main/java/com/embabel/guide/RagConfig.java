package com.embabel.guide;

import com.embabel.agent.rag.RagService;
import com.embabel.agent.rag.RagServiceEnhancer;
import com.embabel.agent.rag.RagServiceEnhancerProperties;
import com.embabel.agent.rag.pipeline.PipelinedRagServiceEnhancer;
import com.embabel.agent.rag.support.FacetedRagService;
import com.embabel.agent.rag.support.RagFacetProvider;
import org.neo4j.ogm.session.SessionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

import java.util.Collections;
import java.util.List;

@Configuration
@EnableNeo4jRepositories(basePackages = "com.embabel.guide.domain")
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

    // TODO override because properties binding of packages list doesn't work
    @Bean
    SessionFactory sessionFactory(org.neo4j.ogm.config.Configuration ogmConfiguration) {
        var sf = new SessionFactory(
                ogmConfiguration,
                "com.embabel.guide.domain"
        );
        return sf;
    }

    @Bean
    public RagServiceEnhancer ragServiceEnhancer(RagServiceEnhancerProperties config) {
        return new PipelinedRagServiceEnhancer(config);
    }

    @Bean
    public RagService ragService(List<RagFacetProvider> facetProviders) {
        return new FacetedRagService(
                "docs",
                "Embabel docs",
                Collections.emptyList(),
                facetProviders
        );
    }

}
