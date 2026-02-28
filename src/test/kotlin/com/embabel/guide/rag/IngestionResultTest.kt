package com.embabel.guide.rag

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration

class IngestionResultTest {

    private fun failure(source: String, reason: String = "test error") =
        IngestionFailure(source, reason)

    @Test
    fun `totalUrls sums loaded and failed`() {
        val result = IngestionResult(
            listOf("a", "b"), listOf(failure("c")),
            emptyList(), emptyList(), emptyList(), Duration.ZERO
        )
        assertEquals(3, result.totalUrls())
    }

    @Test
    fun `totalDirectories sums ingested and failed`() {
        val result = IngestionResult(
            emptyList(), emptyList(),
            listOf("d1"), listOf(failure("d2"), failure("d3")),
            emptyList(), Duration.ZERO
        )
        assertEquals(3, result.totalDirectories())
    }

    @Test
    fun `hasFailures returns true when URLs failed`() {
        val result = IngestionResult(
            listOf("ok"), listOf(failure("bad")),
            emptyList(), emptyList(), emptyList(), Duration.ZERO
        )
        assertTrue(result.hasFailures())
    }

    @Test
    fun `hasFailures returns true when directories failed`() {
        val result = IngestionResult(
            emptyList(), emptyList(),
            emptyList(), listOf(failure("bad-dir")),
            emptyList(), Duration.ZERO
        )
        assertTrue(result.hasFailures())
    }

    @Test
    fun `hasFailures returns true when documents failed`() {
        val result = IngestionResult(
            emptyList(), emptyList(),
            listOf("dir"), emptyList(),
            listOf(failure("dir -> doc1")),
            Duration.ZERO
        )
        assertTrue(result.hasFailures())
    }

    @Test
    fun `hasFailures returns false when nothing failed`() {
        val result = IngestionResult(
            listOf("ok"), emptyList(),
            listOf("dir"), emptyList(), emptyList(), Duration.ZERO
        )
        assertFalse(result.hasFailures())
    }

    @Test
    fun `empty result has zero totals and no failures`() {
        val result = IngestionResult(
            emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList(), Duration.ZERO
        )
        assertEquals(0, result.totalUrls())
        assertEquals(0, result.totalDirectories())
        assertEquals(0, result.totalFailures())
        assertFalse(result.hasFailures())
    }

    @Test
    fun `totalFailures counts all failure types`() {
        val result = IngestionResult(
            emptyList(), listOf(failure("u1"), failure("u2")),
            emptyList(), listOf(failure("d1")),
            listOf(failure("doc1"), failure("doc2"), failure("doc3")),
            Duration.ZERO
        )
        assertEquals(6, result.totalFailures())
    }

    @Test
    fun `elapsed duration is preserved`() {
        val duration = Duration.ofMinutes(5)
        val result = IngestionResult(
            emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList(), duration
        )
        assertEquals(duration, result.elapsed())
    }
}
