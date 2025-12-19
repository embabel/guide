package com.embabel.guide.rag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Web endpoints for ingestion
 */
@RestController
@RequestMapping("/api/v1/data")
public class DataManagerController {

    private final DataManager dataManager;

    public DataManagerController(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    @GetMapping("/stats")
    public DataManager.Stats getStats() {
        return dataManager.getStats();
    }

    @PostMapping("/load-references")
    public String loadReferences() {
        dataManager.loadReferences();
        return "References loaded successfully";
    }
}
