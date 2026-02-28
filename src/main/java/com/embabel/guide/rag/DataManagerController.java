package com.embabel.guide.rag;

import com.embabel.agent.rag.store.ContentElementRepositoryInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Web endpoints for ingestion and content management
 */
@RestController
@RequestMapping("/api/v1/data")
public class DataManagerController {

    private final DataManager dataManager;

    public DataManagerController(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    @GetMapping("/stats")
    public ContentElementRepositoryInfo getStats() {
        return dataManager.getStats();
    }

    @PostMapping("/load-references")
    public IngestionResult loadReferences() {
        return dataManager.loadReferences();
    }
}
