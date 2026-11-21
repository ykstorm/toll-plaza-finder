package dev.lakshyaraj.tollplazas.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "tolls")
public record TollPlazaProperties(@Valid @NotNull Data data,
                                  @Valid @NotNull Matching matching,
                                  @Valid @NotNull Routing routing,
                                  @Valid @NotNull Cache cache,
                                  @Valid @NotNull RateLimit rateLimit) {

    public record Data(@NotNull Resource tollPlazaCsv, @NotNull Resource pincodeCsv, boolean importOnStartup) {
    }

        public record Matching(@Positive double corridorMeters, @Positive double duplicateWindowMeters,
                           @Positive double sameSiteMeters) {
    }

        public record Routing(@NotBlank String osrmBaseUrl, @NotNull Duration connectTimeout,
                          @NotNull Duration readTimeout, @NotBlank String userAgent,
                          @Positive double maxSnapMeters) {
    }

    public record Cache(@Min(1) long memoryMaxEntries, @NotNull Duration memoryTtl, @NotNull Duration databaseTtl) {
    }

    public record RateLimit(@Min(1) int requestsPerMinute) {
    }
}
