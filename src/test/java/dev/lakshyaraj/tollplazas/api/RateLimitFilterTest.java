package dev.lakshyaraj.tollplazas.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RateLimitFilterTest {

    // a clock the test moves by hand, so nothing here depends on real time
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-11-21T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final TestClock clock = new TestClock();
    private final RateLimitFilter filter = new RateLimitFilter(3, clock);

    private MockHttpServletResponse call(String ip, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void letsABurstUpToTheLimitThrough() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(call("1.2.3.4", "/api/v1/toll-plazas").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void blocksTheNextOneWithRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) {
            call("1.2.3.4", "/api/v1/toll-plazas");
        }

        MockHttpServletResponse blocked = call("1.2.3.4", "/api/v1/toll-plazas");

        assertThat(blocked.getStatus()).isEqualTo(429);
        // 3 a minute = one token every 20s
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("20");
        assertThat(blocked.getContentAsString()).contains("\"error\"");
    }

    @Test
    void refillsOverTime() throws Exception {
        for (int i = 0; i < 3; i++) {
            call("1.2.3.4", "/api/v1/toll-plazas");
        }
        clock.advance(Duration.ofSeconds(20));

        assertThat(call("1.2.3.4", "/api/v1/toll-plazas").getStatus()).isEqualTo(200);
        assertThat(call("1.2.3.4", "/api/v1/toll-plazas").getStatus()).isEqualTo(429);
    }

    @Test
    void neverRefillsPastCapacity() throws Exception {
        clock.advance(Duration.ofHours(1));
        for (int i = 0; i < 3; i++) {
            call("1.2.3.4", "/api/v1/toll-plazas");
        }
        assertThat(call("1.2.3.4", "/api/v1/toll-plazas").getStatus()).isEqualTo(429);
    }

    @Test
    void eachClientHasItsOwnBucket() throws Exception {
        for (int i = 0; i < 3; i++) {
            call("1.2.3.4", "/api/v1/toll-plazas");
        }
        assertThat(call("5.6.7.8", "/api/v1/toll-plazas").getStatus()).isEqualTo(200);
    }

    @Test
    void docsAndHealthAreNotLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(call("1.2.3.4", "/swagger-ui.html").getStatus()).isEqualTo(200);
            assertThat(call("1.2.3.4", "/actuator/health").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void zeroLimitMakesNoSense() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RateLimitFilter(0, clock));
    }
}
