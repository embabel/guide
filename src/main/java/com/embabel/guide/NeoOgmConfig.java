package com.embabel.guide;

import org.neo4j.ogm.session.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.List;

@ConfigurationProperties(prefix = "embabel.neo4j")
class NeoOgmConfigProperties {
    private List<String> packages;

    public List<String> getPackages() {
        return packages;
    }

    public void setPackages(List<String> packages) {
        this.packages = packages;
    }
}

@Configuration
@EnableNeo4jRepositories
@EnableTransactionManagement
public class NeoOgmConfig {

    private static final Logger logger = LoggerFactory.getLogger(NeoOgmConfig.class);

    private final String uri;
    private final String username;
    private final String password;
    private final NeoOgmConfigProperties properties;

    public NeoOgmConfig(
            @Value("${spring.neo4j.uri}") String uri,
            @Value("${spring.neo4j.authentication.username}") String username,
            @Value("${spring.neo4j.authentication.password}") String password,
            NeoOgmConfigProperties properties) {
        this.uri = uri;
        this.username = username;
        this.password = password;
        this.properties = properties;
    }

    @Bean
    public org.neo4j.ogm.config.Configuration configuration() {
        logger.info("Connecting to Neo4j at {} as user {}", uri, username);
        return new org.neo4j.ogm.config.Configuration.Builder()
                .uri(uri)
                .credentials(username, password)
                .build();
    }

    @Bean
    public SessionFactory sessionFactory() {
        return new SessionFactory(
                configuration(),
                properties.getPackages().toArray(new String[0])
        );
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager() {
        return new org.springframework.data.neo4j.transaction.Neo4jTransactionManager(sessionFactory());
    }
}
