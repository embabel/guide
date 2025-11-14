package com.embabel.guide.config;

import org.drivine.connection.ConnectionProperties;
import org.drivine.connection.DataSourceMap;
import org.drivine.connection.DatabaseType;
import org.drivine.manager.PersistenceManager;
import org.drivine.manager.PersistenceManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuration for Drivine4j Neo4j client.
 * This provides an alternative to Neo4j OGM with a composition-based approach.
 */
@Configuration
@ComponentScan("org.drivine")
public class DrivineConfig {

    @Value("${spring.neo4j.uri:bolt://localhost:7687}")
    private String neo4jUri;

    @Value("${spring.neo4j.authentication.username:neo4j}")
    private String username;

    @Value("${spring.neo4j.authentication.password:brahmsian}")
    private String password;

    @Value("${spring.data.neo4j.database:neo4j}")
    private String database;

    @Bean
    @Profile("local & !test")
    public DataSourceMap dataSourceMap() {
        // Parse the URI to extract host and port
        String host = "localhost";
        int port = 7687;

        if (neo4jUri.startsWith("bolt://")) {
            String hostPort = neo4jUri.substring(7);
            String[] parts = hostPort.split(":");
            if (parts.length > 0) {
                host = parts[0];
            }
            if (parts.length > 1) {
                port = Integer.parseInt(parts[1]);
            }
        }

        ConnectionProperties props = new ConnectionProperties(
            DatabaseType.NEO4J,
            host,
            port,
            username,
            password,
            null,
            null,
            null,
            null,
            null,
            null
        );

        return new DataSourceMap(java.util.Map.of("neo", props));
    }

    @Bean("neo")
    public PersistenceManager neoManager(PersistenceManagerFactory factory) {
        return factory.get("neo");
    }
}
