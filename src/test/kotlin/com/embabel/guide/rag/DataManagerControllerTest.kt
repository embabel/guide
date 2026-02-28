package com.embabel.guide.rag

import com.embabel.agent.rag.neo.drivine.model.ContentElementRepositoryInfoImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Duration

class DataManagerControllerTest {

    private val dataManager = mock(DataManager::class.java)
    private val controller = DataManagerController(dataManager)

    @Test
    fun `getStats delegates to dataManager`() {
        val stats = ContentElementRepositoryInfoImpl(10, 3, 20, false, true)
        `when`(dataManager.getStats()).thenReturn(stats)

        val result = controller.getStats()

        assertEquals(stats, result)
        verify(dataManager).getStats()
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
