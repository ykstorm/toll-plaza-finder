package dev.lakshyaraj.tollplazas.api;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

// token bucket per client ip. the cache soaks up repeat routes, but every new pincode pair
// is an OSRM call, and the public OSRM server will ban us if someone loops over pincodes.
// ip comes from getRemoteAddr, which is the real client because forward-headers-strategy is on.
public class RateLimitFilter extends OncePerRequestFilter {

    private final int capacity;
    private final double tokensPerNano;
    private final Clock clock;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(100_000)
            .build();

    public RateLimitFilter(int requestsPerMinute, Clock clock) {
        if (requestsPerMinute < 1) {
            throw new IllegalArgumentException("need at least 1 request a minute");
        }
        this.capacity = requestsPerMinute;
        this.tokensPerNano = requestsPerMinute / (double) Duration.ofMinutes(1).toNanos();
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Bucket bucket = buckets.get(request.getRemoteAddr(), ip -> new Bucket(capacity, nanos()));
        long waitSeconds = bucket.tryTake(nanos(), capacity, tokensPerNano);
        if (waitSeconds == 0) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(waitSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Too many requests, try again in " + waitSeconds + "s\"}");
    }

    private long nanos() {
        return clock.millis() * 1_000_000L;
    }

    // synchronized per bucket, so two requests from the same ip can't both take the last token
    private static final class Bucket {
        private double tokens;
        private long lastRefill;

        Bucket(int capacity, long now) {
            this.tokens = capacity;
            this.lastRefill = now;
        }

        // 0 means go ahead, anything else is how many seconds until a token is free
        synchronized long tryTake(long now, int capacity, double tokensPerNano) {
            tokens = Math.min(capacity, tokens + (now - lastRefill) * tokensPerNano);
            lastRefill = now;
            if (tokens >= 1) {
                tokens -= 1;
                return 0;
            }
            double nanosUntilOne = (1 - tokens) / tokensPerNano;
            return Math.max(1, (long) Math.ceil(nanosUntilOne / 1_000_000_000d));
        }
    }
}
