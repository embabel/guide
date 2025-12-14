package com.embabel.guide.rag;

import com.embabel.agent.mcpserver.McpToolExport;
import com.embabel.agent.rag.neo.drivine.DrivineStore;
import com.embabel.agent.rag.tools.ToolishRag;
import com.embabel.guide.GuideProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class McpToolExportConfiguration {

    @Bean
    McpToolExport documentationRagTools(
            DrivineStore drivineStore,
            GuideProperties properties
    ) {
        var toolishRag = new ToolishRag(
                "docs",
                "Embabel docs",
                drivineStore
        );
        return McpToolExport.fromLlmReference(
                toolishRag,
                properties.toolNamingStrategy()
        );
    }

    @Bean
    McpToolExport codeReferenceTools(
            DataManager dataManager,
            GuideProperties properties) {
        return McpToolExport.fromLlmReferences(
                dataManager.referencesForAllUsers(),
                properties.toolNamingStrategy()
        );
    }
}
