package com.embabel.guide.rag;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data")
public class DataManagerController {

    private final DataManager dataManager;

    public DataManagerController(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    @PostMapping("/load-references")
    public String loadReferences() {
        dataManager.loadReferences();
        return "References loaded successfully";
    }
}
