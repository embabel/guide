package com.embabel.guide;

import com.embabel.agent.api.common.AiBuilder;
import com.embabel.agent.api.common.LlmReference;
import com.embabel.agent.rag.ingestion.Ingester;
import com.embabel.agent.rag.ingestion.IngestionUtils;
import com.embabel.agent.tools.file.FileTools;
import com.embabel.chat.ChatSession;
import com.embabel.chat.InMemoryChatbot;
import com.embabel.coding.tools.api.ApiReference;
import com.embabel.coding.tools.git.RepositoryReferenceProvider;
import com.embabel.coding.tools.jvm.ClassGraphApiReferenceExtractor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@Service
public class GuideChatbot extends InMemoryChatbot {

    private final AiBuilder aiBuilder;

    private final Logger logger = LoggerFactory.getLogger(GuideChatbot.class);

    private final List<LlmReference> references = new LinkedList<>();

    public GuideChatbot(
            AiBuilder aiBuilder,
            Ingester ingester) {
        this.aiBuilder = aiBuilder;
        var utils = new IngestionUtils(ingester);
        var ingestionResult = utils.ingestFromDirectory(FileTools.readOnly(
                Path.of(System.getProperty("user.dir"), "data", "docs").toString()
        ));
        logger.info("Ingestion result: {}\nChatbot ready...", ingestionResult);
        var embabelApiReference = new ApiReference(
                new ClassGraphApiReferenceExtractor().fromProjectClasspath(
                        "embabel-agent",
                        Set.of("com.embabel.agent"),
                        Set.of()),
                100);
        var examplesReference = RepositoryReferenceProvider.create()
                .cloneRepository("https://github.com/embabel/embabel-agent-examples.git");
        references.add(embabelApiReference);
        references.add(examplesReference);
    }

    @NotNull
    @Override
    protected ChatSession doCreateSession(@Nullable String systemMessage) {
        return new Guide(aiBuilder, references);
    }

}
