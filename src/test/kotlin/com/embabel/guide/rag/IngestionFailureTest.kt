package com.embabel.guide.rag

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class IngestionFailureTest {

    @Test
    fun `fromException uses exception message`() {
        val ex = RuntimeException("Connection timed out")
        val failure = IngestionFailure.fromException("http://example.com", ex)

        assertEquals("http://example.com", failure.source())
        assertEquals("Connection timed out", failure.reason())
    }

    @Test
    fun `fromException falls back to class name when message is null`() {
        val ex = NullPointerException()
        val failure = IngestionFailure.fromException("/some/dir", ex)

        assertEquals("/some/dir", failure.source())
        assertEquals("NullPointerException", failure.reason())
    }

    @Test
    fun `fromException falls back to class name when message is blank`() {
        val ex = RuntimeException("   ")
        val failure = IngestionFailure.fromException("/some/dir", ex)

        assertEquals("/some/dir", failure.source())
        assertEquals("RuntimeException", failure.reason())
    }

    @Test
    fun `record fields are accessible`() {
        val failure = IngestionFailure("src", "reason")
        assertEquals("src", failure.source())
        assertEquals("reason", failure.reason())
    }
}
