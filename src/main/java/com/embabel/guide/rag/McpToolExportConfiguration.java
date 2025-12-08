package com.embabel.guide.rag;

import com.embabel.agent.mcpserver.McpToolExport;
import com.embabel.agent.rag.neo.drivine.DrivineStore;
import com.embabel.agent.rag.tools.ToolishRag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolExportConfiguration {

    @Bean
    McpToolExport documentationRagTools(DrivineStore drivineStore) {
        var toolishRag = new ToolishRag(
                "docs",
                "Embabel docs",
                drivineStore
        );
        return McpToolExport.fromLlmReference(toolishRag);
    }

    @Bean
    McpToolExport codeReferenceTools(DataManager dataManager) {
        return McpToolExport.fromLlmReferences(
                dataManager.referencesForAllUsers()
        );
    }
}
