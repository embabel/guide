package com.embabel.guide.domain;

import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface GuideUserRepository extends Neo4jRepository<GuideUser, String> {
}
