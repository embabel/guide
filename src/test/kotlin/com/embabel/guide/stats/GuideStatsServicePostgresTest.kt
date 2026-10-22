package com.embabel.guide.stats

import org.springframework.test.context.ActiveProfiles

/** [GuideStatsServiceTest] on Postgres, which Drivine reads with plain SQL and pgvector. */
@ActiveProfiles("test-postgres")
class GuideStatsServicePostgresTest : GuideStatsServiceTest()
