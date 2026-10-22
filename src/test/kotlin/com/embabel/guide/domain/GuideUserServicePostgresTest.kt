package com.embabel.guide.domain

import org.springframework.test.context.ActiveProfiles

/** [GuideUserServiceTest] on Postgres, which Drivine reads with plain SQL and pgvector. */
@ActiveProfiles("test-postgres")
class GuideUserServicePostgresTest : GuideUserServiceTest()
