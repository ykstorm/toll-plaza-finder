package dev.lakshyaraj.tollplazas.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

// Neon, Render, Heroku and friends all hand out DATABASE_URL as postgres://user:pass@host/db.
// spring wants jdbc:postgresql://host/db with the user and password separate.
// so if DATABASE_URL is the postgres:// kind, split it up here and you can paste it as-is.
public class DatabaseUrlPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        String raw = env.getProperty("DATABASE_URL");
        if (raw == null || !(raw.startsWith("postgres://") || raw.startsWith("postgresql://"))) {
            return;
        }
        env.getPropertySources().addFirst(new MapPropertySource("database-url", toSpringProperties(raw)));
    }

    static Map<String, Object> toSpringProperties(String raw) {
        URI uri = URI.create(raw);
        Map<String, Object> props = new HashMap<>();
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        props.put("spring.datasource.url", "jdbc:postgresql://" + uri.getHost() + port + uri.getRawPath() + query);

        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            int colon = userInfo.indexOf(':');
            String user = colon < 0 ? userInfo : userInfo.substring(0, colon);
            props.put("spring.datasource.username", URLDecoder.decode(user, StandardCharsets.UTF_8));
            if (colon >= 0) {
                // passwords with @ or : come url-encoded
                props.put("spring.datasource.password",
                        URLDecoder.decode(userInfo.substring(colon + 1), StandardCharsets.UTF_8));
            }
        }
        return props;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
