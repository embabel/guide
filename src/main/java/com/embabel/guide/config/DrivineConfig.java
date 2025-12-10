package com.embabel.guide.config;

import org.drivine.autoconfigure.EnableDrivine;
import org.drivine.autoconfigure.EnableDrivinePropertiesConfig;
import org.drivine.manager.GraphObjectManager;
import org.drivine.manager.GraphObjectManagerFactory;
import org.drivine.manager.PersistenceManager;
import org.drivine.manager.PersistenceManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Drivine4j Neo4j client.
 * This provides an alternative to Neo4j OGM with a composition-based approach.
 * <p>
 * Uses annotation-based configuration introduced in Drivine 0.0.3-SNAPSHOT:
 * - @EnableDrivine: Enables core Drivine infrastructure beans
 * - @EnableDrivinePropertiesConfig: Auto-configures DataSourceMap from application.yml
 */
@Configuration
@EnableDrivine
@EnableDrivinePropertiesConfig
public class DrivineConfig {

    @Bean("neo")
    public PersistenceManager neoManager(PersistenceManagerFactory factory) {
        return factory.get("neo");
    }

    @Bean
    public GraphObjectManager graphObjectManager(GraphObjectManagerFactory factory) {
        return factory.get("neo");
    }
}
