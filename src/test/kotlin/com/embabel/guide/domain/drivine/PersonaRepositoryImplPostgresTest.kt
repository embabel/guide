package com.embabel.guide.domain.drivine

import org.springframework.test.context.ActiveProfiles

/** [PersonaRepositoryImplTest] on Postgres, which Drivine reads with plain SQL and pgvector. */
@ActiveProfiles("test-postgres")
class PersonaRepositoryImplPostgresTest : PersonaRepositoryImplTest()
