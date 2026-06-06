package com.embabel.guide.rag

import com.embabel.agent.rag.graph.model.ContentElementRepositoryInfoImpl
import com.embabel.guide.stats.GuideStats
import com.embabel.guide.stats.GuideStatsService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Duration

class DataManagerControllerTest {

    private val dataManager = mock(DataManager::class.java)
    private val guideStatsService = mock(GuideStatsService::class.java)
    private val controller = DataManagerController(dataManager, guideStatsService)

    @Test
    fun `getStats delegates to guideStatsService for the current caller`() {
        // No SecurityContext in a plain unit test -> caller resolves to null (anonymous).
        val stats = GuideStats(content = ContentElementRepositoryInfoImpl(10, 3, 20, false, true))
        `when`(guideStatsService.stats(null)).thenReturn(stats)

        val result = controller.getStats()

        assertEquals(stats, result)
        verify(guideStatsService).stats(null)
    }

    @Test
    fun `loadReferences returns IngestionResult from dataManager`() {
        val ingestionResult = IngestionResult(
            listOf("http://example.com"),
            emptyList(),
            listOf("/dir"),
            emptyList(),
            emptyList(),
            Duration.ofSeconds(60)
        )
        `when`(dataManager.loadReferences()).thenReturn(ingestionResult)

        val result = controller.loadReferences()

        assertEquals(ingestionResult, result)
        assertEquals(1, result.loadedUrls().size)
        assertEquals(1, result.ingestedDirectories().size)
        verify(dataManager).loadReferences()
    }
}
