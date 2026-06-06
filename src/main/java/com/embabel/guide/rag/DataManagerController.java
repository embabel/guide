package com.embabel.guide.rag;

import com.embabel.guide.stats.GuideStats;
import com.embabel.guide.stats.GuideStatsService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final GuideStatsService guideStatsService;

    public DataManagerController(DataManager dataManager, GuideStatsService guideStatsService) {
        this.dataManager = dataManager;
        this.guideStatsService = guideStatsService;
    }

    /**
     * Public stats. Content counts are returned to everyone; admin callers additionally receive
     * user/message figures. The role check is performed in {@link GuideStatsService} from the
     * authenticated caller's roles.
     */
    @GetMapping("/stats")
    public GuideStats getStats() {
        return guideStatsService.stats(currentWebUserId());
    }

    /** The authenticated caller's webUserId (JWT subject), or null when unauthenticated/anonymous. */
    private static String currentWebUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof String principal && !"anonymousUser".equals(principal)) {
            return principal;
        }
        return null;
    }

    @PostMapping("/load-references")
    public IngestionResult loadReferences() {
        return dataManager.loadReferences();
    }
}
