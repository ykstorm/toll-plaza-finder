package dev.lakshyaraj.tollplazas;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

// base for tests that need a real Postgres (the upserts use ON CONFLICT, so H2 won't do).
// Starts a Testcontainers Postgres once per JVM. If IT_DATABASE_URL is set, uses that instead,
// handy on machines without Docker.
public abstract class PostgresIntegrationTest {

    private static final String EXTERNAL_URL = System.getenv("IT_DATABASE_URL");
    private static final PostgreSQLContainer<?> POSTGRES;

    static {
        if (EXTERNAL_URL == null) {
            POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
            POSTGRES.start();
        } else {
            POSTGRES = null;
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        if (POSTGRES != null) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> EXTERNAL_URL);
            registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("IT_DATABASE_USERNAME", "tolls"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("IT_DATABASE_PASSWORD", "tolls"));
        }
    }
}
