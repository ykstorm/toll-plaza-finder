package dev.lakshyaraj.tollplazas.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class DatabaseUrlPostProcessorTest {

    private final DatabaseUrlPostProcessor processor = new DatabaseUrlPostProcessor();

    @Test
    void splitsANeonStyleUrl() {
        var props = DatabaseUrlPostProcessor.toSpringProperties(
                "postgres://tolls_owner:s3cret@ep-cool-river-123.ap-southeast-1.aws.neon.tech/tolls?sslmode=require");

        assertThat(props).containsOnly(
                entry("spring.datasource.url",
                        "jdbc:postgresql://ep-cool-river-123.ap-southeast-1.aws.neon.tech/tolls?sslmode=require"),
                entry("spring.datasource.username", "tolls_owner"),
                entry("spring.datasource.password", "s3cret"));
    }

    @Test
    void keepsThePortAndDecodesAnEncodedPassword() {
        var props = DatabaseUrlPostProcessor.toSpringProperties("postgresql://app:p%40ss%3Aword@db.internal:6543/tolls");

        assertThat(props.get("spring.datasource.url")).isEqualTo("jdbc:postgresql://db.internal:6543/tolls");
        assertThat(props.get("spring.datasource.password")).isEqualTo("p@ss:word");
    }

    @Test
    void overridesTheDatasourceWhenGivenAPostgresUrl() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("DATABASE_URL", "postgres://u:p@host/db")
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost/old");

        processor.postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://host/db");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("u");
    }

    @Test
    void leavesAJdbcUrlAlone() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("DATABASE_URL", "jdbc:postgresql://localhost:5432/tolls");

        processor.postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getPropertySources().contains("database-url")).isFalse();
    }

    @Test
    void doesNothingWithoutDatabaseUrl() {
        MockEnvironment env = new MockEnvironment();
        processor.postProcessEnvironment(env, new SpringApplication());
        assertThat(env.getPropertySources().contains("database-url")).isFalse();
    }
}
