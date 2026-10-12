package com.embabel.guide.rag;

import com.embabel.agent.filter.PropertyFilter;
import com.embabel.agent.mcpserver.McpToolExport;
import com.embabel.agent.rag.graph.GraphRagStore;
import com.embabel.agent.rag.tools.ToolishRag;
import com.embabel.guide.GuideProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Export MCP tools
 */
@Configuration
class McpToolExportConfiguration {

    @Bean
    McpToolExport documentationRagTools(
            GraphRagStore drivineStore,
            GuideProperties properties
    ) {
        var toolishRag = new ToolishRag(
                "docs",
                "Embabel docs",
                drivineStore
        );
        var activeVersion = properties.getContent().getActiveVersion();
        if (activeVersion != null) {
            var versionFilter = new PropertyFilter.In(
                    "version",
                    List.of(activeVersion, VersionChunkTransformer.SUPPLEMENTARY)
            );
            toolishRag = toolishRag.withMetadataFilter(versionFilter);
        }
        return McpToolExport.fromLlmReference(
                toolishRag,
                properties.toolNamingStrategy()
        );
    }

    @Bean
    McpToolExport referenceTools(
            DataManager dataManager,
            GuideProperties properties) {
        return McpToolExport.fromLlmReferences(
                dataManager.referencesForAllUsers(),
                properties.toolNamingStrategy()
        );
    }
}
