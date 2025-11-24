package com.embabel.guide.rag;

import com.embabel.agent.rag.pipeline.HyDEQueryGenerator;
import com.embabel.agent.rag.pipeline.PipelinedRagServiceEnhancer;
import com.embabel.agent.rag.service.RagService;
import com.embabel.agent.rag.service.RagServiceEnhancer;
import com.embabel.agent.rag.service.RagServiceEnhancerProperties;
import com.embabel.agent.rag.service.support.FacetedRagService;
import com.embabel.agent.rag.service.support.RagFacetProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
class RagConfig {

    /**
     * Add enhancements to basic RAG
     */
    @Bean
    RagServiceEnhancer ragServiceEnhancer(RagServiceEnhancerProperties config, HyDEQueryGenerator hyDEQueryGenerator) {
        return new PipelinedRagServiceEnhancer(config, hyDEQueryGenerator);
    }

    @Bean
    RagService ragService(List<RagFacetProvider> facetProviders) {
        return new FacetedRagService("docs", "Embabel docs", List.of(), facetProviders);
    }

}
