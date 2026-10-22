package com.embabel.guide.domain.drivine

import org.springframework.test.context.ActiveProfiles

/** [GuideUserRepositoryDefaultImplTest] on Postgres, which Drivine reads with plain SQL and pgvector. */
@ActiveProfiles("test-postgres")
class GuideUserRepositoryDefaultImplPostgresTest : GuideUserRepositoryDefaultImplTest()
