package com.embabel.guide.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Provisions the RAG store's schema once the context is up, rather than while beans are being
 * constructed.
 *
 * <p>Provisioning creates the vector index, and a vector index cannot be created without knowing
 * its width. The width comes from the embedding model, which in a bring-your-own-key deployment
 * comes from a key that arrives at first-run setup rather than at boot. Doing this in
 * {@link DataManager}'s constructor made merely building the bean interrogate the embedding
 * service, so a deployment holding no key yet could not start at all — the failure landed at
 * context refresh instead of at the first operation that actually needed an index.
 *
 * <p>Ingestion moved out of that constructor for the same reason and became
 * {@link IngestionRunner}. This is the straggler. Ordered ahead of ingestion, because ingestion
 * writes into the indexes this creates.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProvisioningRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(ProvisioningRunner.class);

    private final DataManager dataManager;

    public ProvisioningRunner(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    @Override
    public void run(ApplicationArguments args) {
        logger.info("Provisioning RAG store schema");
        dataManager.provisionDatabase();
    }
}
