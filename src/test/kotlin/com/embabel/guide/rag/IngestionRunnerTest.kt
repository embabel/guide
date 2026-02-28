package com.embabel.guide.rag

import com.embabel.agent.rag.neo.drivine.model.ContentElementRepositoryInfoImpl
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.boot.DefaultApplicationArguments
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.time.Duration

class IngestionRunnerTest {

    private val dataManager = mock(DataManager::class.java)

    private fun failure(source: String, reason: String = "test error") =
        IngestionFailure(source, reason)

    private fun createRunner(port: Int = 1337): IngestionRunner {
        val runner = IngestionRunner(dataManager)
        val field = IngestionRunner::class.java.getDeclaredField("serverPort")
        field.isAccessible = true
        field.setInt(runner, port)
        return runner
    }

    @Test
    fun `run calls loadReferences and getStats`() {
        val result = IngestionResult(
            listOf("http://example.com"), emptyList(),
            emptyList(), emptyList(), emptyList(),
            Duration.ofSeconds(10)
        )
        `when`(dataManager.loadReferences()).thenReturn(result)
        `when`(dataManager.getStats()).thenReturn(ContentElementRepositoryInfoImpl(5, 2, 10, false, true))

        val runner = createRunner()
        runner.run(DefaultApplicationArguments())

        verify(dataManager).loadReferences()
        verify(dataManager).getStats()
    }

    @Test
    fun `summary banner contains URL results`() {
        val result = IngestionResult(
            listOf("http://loaded.com"),
            listOf(failure("http://failed.com", "Connection refused")),
            emptyList(), emptyList(), emptyList(),
            Duration.ofSeconds(30)
        )
        `when`(dataManager.loadReferences()).thenReturn(result)
        `when`(dataManager.getStats()).thenReturn(ContentElementRepositoryInfoImpl(0, 0, 0, false, true))

        val output = captureStdout {
            createRunner().run(DefaultApplicationArguments())
        }

        assertTrue(output.contains("INGESTION COMPLETE"), "Should contain banner")
        assertTrue(output.contains("http://loaded.com"), "Should list loaded URL")
        assertTrue(output.contains("http://failed.com"), "Should list failed URL")
        assertTrue(output.contains("Connection refused"), "Should show failure reason")
        assertTrue(output.contains("1/2 loaded"), "Should show URL counts")
    }

    @Test
    fun `summary banner contains directory results`() {
        val result = IngestionResult(
            emptyList(), emptyList(),
            listOf("/home/user/repo"),
            listOf(failure("/home/user/bad", "No such directory")),
            emptyList(),
            Duration.ofMinutes(5)
        )
        `when`(dataManager.loadReferences()).thenReturn(result)
        `when`(dataManager.getStats()).thenReturn(ContentElementRepositoryInfoImpl(100, 10, 200, false, true))

        val output = captureStdout {
            createRunner().run(DefaultApplicationArguments())
        }

        assertTrue(output.contains("/home/user/repo"), "Should list ingested dir")
        assertTrue(output.contains("/home/user/bad"), "Should list failed dir")
        assertTrue(output.contains("No such directory"), "Should show dir failure reason")
        assertTrue(output.contains("1/2 ingested"), "Should show dir counts")
    }

    @Test
    fun `summary shows document failures`() {
        val result = IngestionResult(
            emptyList(), emptyList(),
            listOf("/home/user/repo"), emptyList(),
            listOf(failure("/home/user/repo -> README.md", "Parse error: invalid encoding")),
            Duration.ofSeconds(20)
        )
        `when`(dataManager.loadReferences()).thenReturn(result)
        `when`(dataManager.getStats()).thenReturn(ContentElementRepositoryInfoImpl(50, 5, 100, false, true))

        val output = captureStdout {
            createRunner().run(DefaultApplicationArguments())
        }

        assertTrue(output.contains("Document Failures (1)"), "Should show document failure section")
        assertTrue(output.contains("README.md"), "Should show failed document")
        assertTrue(output.contains("Parse error: invalid encoding"), "Should show doc failure reason")
    }

    @Test
    fun `summary shows no directories configured when empty`() {
        val result = IngestionResult(
            emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList(),
            Duration.ofSeconds(1)
        )
        `when`(dataManager.loadReferences()).thenReturn(result)
        `when`(dataManager.getStats()).thenReturn(ContentElementRepositoryInfoImpl(0, 0, 0, false, true))

        val output = captureStdout {
            createRunner().run(DefaultApplicationArguments())
        }

        assertTrue(output.contains("none configured"), "Should say no directories")
    }

    @Test
    fun `summary shows RAG store stats`() {
        val result = IngestionResult(
            emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList(), Duration.ZERO
        )
        `when`(dataManager.loadReferences()).thenReturn(result)
        `when`(dataManager.getStats()).thenReturn(ContentElementRepositoryInfoImpl(42, 7, 100, false, true))

        val output = captureStdout {
            createRunner().run(DefaultApplicationArguments())
        }

        assertTrue(output.contains("Documents: 7"), "Should show document count")
        assertTrue(output.contains("Chunks:    42"), "Should show chunk count")
        assertTrue(output.contains("Elements:  100"), "Should show element count")
    }

    @Test
    fun `summary shows port and MCP endpoint`() {
        val result = IngestionResult(
            emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList(), Duration.ZERO
        )
        `when`(dataManager.loadReferences()).thenReturn(result)
        `when`(dataManager.getStats()).thenReturn(ContentElementRepositoryInfoImpl(0, 0, 0, false, true))

        val output = captureStdout {
            createRunner(port = 9999).run(DefaultApplicationArguments())
        }

        assertTrue(output.contains("port 9999"), "Should show port")
        assertTrue(output.contains("http://localhost:9999/sse"), "Should show MCP endpoint")
    }

    @Test
    fun `formatDuration shows seconds for short durations`() {
        val result = IngestionResult(
            emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList(),
            Duration.ofSeconds(45)
        )
        `when`(dataManager.loadReferences()).thenReturn(result)
        `when`(dataManager.getStats()).thenReturn(ContentElementRepositoryInfoImpl(0, 0, 0, false, true))

        val output = captureStdout {
            createRunner().run(DefaultApplicationArguments())
        }

        assertTrue(output.contains("45s"), "Should format as seconds")
    }

    @Test
    fun `formatDuration shows minutes and seconds for longer durations`() {
        val result = IngestionResult(
            emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList(),
            Duration.ofSeconds(125)
        )
        `when`(dataManager.loadReferences()).thenReturn(result)
        `when`(dataManager.getStats()).thenReturn(ContentElementRepositoryInfoImpl(0, 0, 0, false, true))

        val output = captureStdout {
            createRunner().run(DefaultApplicationArguments())
        }

        assertTrue(output.contains("2m 5s"), "Should format as minutes + seconds")
    }

    private fun captureStdout(block: () -> Unit): String {
        val original = System.out
        val baos = ByteArrayOutputStream()
        System.setOut(PrintStream(baos))
        try {
            block()
        } finally {
            System.setOut(original)
        }
        return baos.toString()
    }
}
