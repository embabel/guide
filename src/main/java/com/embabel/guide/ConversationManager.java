package com.embabel.guide;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.rag.Ingester;
import com.embabel.chat.ChatSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ConversationManager {

    private final Ai ai;
    private final Ingester ingester;

    private final Logger logger = LoggerFactory.getLogger(ConversationManager.class);

    public ConversationManager(
            Ai ai,
            Ingester ingester) {
        this.ingester = ingester;
        this.ai = ai;
        var ingestionResult = ingester.ingest("data/index.md");
        logger.info("Ingestion result: {}", ingestionResult);
    }
    
    public ChatSession guide() {
        return new Guide(ai);
    }
}
