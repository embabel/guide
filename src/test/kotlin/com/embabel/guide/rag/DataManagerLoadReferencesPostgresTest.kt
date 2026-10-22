package com.embabel.guide.rag

import org.springframework.test.context.ActiveProfiles

/** [DataManagerLoadReferencesIntegrationTest] on Postgres, which Drivine reads with plain SQL and pgvector. */
@ActiveProfiles("test-postgres")
class DataManagerLoadReferencesPostgresTest : DataManagerLoadReferencesIntegrationTest()
