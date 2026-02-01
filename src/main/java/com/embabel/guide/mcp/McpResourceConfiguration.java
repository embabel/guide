package com.embabel.guide.mcp;

import com.embabel.agent.mcpserver.sync.McpResourcePublisher;
import com.embabel.agent.mcpserver.sync.SyncResourceSpecificationFactory;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class McpResourceConfiguration {

    private static final String ABOUT_CONTENT = """
Embabel Guide MCP server (SSE)

Purpose:
- Exposes Embabel documentation and API references to MCP clients.
- Primary use is answering Embabel questions using MCP tools (docs_* and API lookup tools).

Endpoints:
- SSE: http://localhost:1337/sse
- Tools list: http://localhost:1337/mcp/tools/list
- Resources list: http://localhost:1337/mcp/resources/list

Tool naming:
- docs_* tools search Embabel documentation content.
- embabel_agent_* tools resolve API signatures from Embabel packages.

Recommended usage for agents:
1) Prefer docs_* tools to answer Embabel questions.
2) Use embabel_agent_* tools to confirm class/package signatures.
3) If unsure, query tools list to see exact available tools.

Notes:
- If running on a different port, update the SSE URL accordingly.
- This server currently exposes MCP tools and resources (no MCP prompts).
""";

    @Bean
    public McpResourcePublisher guideResources() {
        return new McpResourcePublisher() {
            @Override
            public List<SyncResourceSpecification> resources() {
                return List.of(
                        SyncResourceSpecificationFactory.staticSyncResourceSpecification(
                                "embabel://guide/about",
                                "about",
                                "About this MCP server and how to use it",
                                ABOUT_CONTENT,
                                "text/plain"
                        )
                );
            }

            @Override
            public String infoString(Boolean verbose, int indent) {
                return "Embabel Guide MCP resources";
            }
        };
    }
}
