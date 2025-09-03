package com.embabel.guide;

import com.embabel.agent.api.common.AiBuilder;
import com.embabel.agent.rag.ingestion.Ingester;
import com.embabel.agent.rag.ingestion.IngestionUtils;
import com.embabel.agent.tools.file.FileTools;
import com.embabel.chat.ChatSession;
import com.embabel.chat.InMemoryChatbot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class GuideChatbot extends InMemoryChatbot {

    private final AiBuilder aiBuilder;
    private final Ingester ingester;

    private final Logger logger = LoggerFactory.getLogger(GuideChatbot.class);

    public GuideChatbot(
            AiBuilder aiBuilder,
            Ingester ingester) {
        this.ingester = ingester;
        this.aiBuilder = aiBuilder;
        var utils = new IngestionUtils(ingester);
        var ingestionResult = utils.ingestFromDirectory(FileTools.readOnly(
                Path.of(System.getProperty("user.dir"), "data", "docs").toString()
        ));
        logger.info("Ingestion result: {}\nChatbot ready...", ingestionResult);
    }

    @NotNull
    @Override
    protected ChatSession doCreateSession(@Nullable String systemMessage) {
        return new Guide(aiBuilder);
    }

}
