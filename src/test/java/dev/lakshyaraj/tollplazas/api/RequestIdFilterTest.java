package dev.lakshyaraj.tollplazas.api;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    private MockHttpServletResponse run(String incomingId, AtomicReference<String> seenInMdc) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/toll-plazas");
        if (incomingId != null) {
            request.addHeader("X-Request-Id", incomingId);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> seenInMdc.set(MDC.get("requestId"));
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void makesAnIdWhenTheCallerDoesNotSendOne() throws Exception {
        AtomicReference<String> mdc = new AtomicReference<>();
        MockHttpServletResponse response = run(null, mdc);

        assertThat(response.getHeader("X-Request-Id")).hasSize(8);
        assertThat(mdc.get()).isEqualTo(response.getHeader("X-Request-Id"));
    }

    @Test
    void keepsTheCallersIdSoTheyCanTraceIt() throws Exception {
        AtomicReference<String> mdc = new AtomicReference<>();
        assertThat(run("checkout-42", mdc).getHeader("X-Request-Id")).isEqualTo("checkout-42");
        assertThat(mdc.get()).isEqualTo("checkout-42");
    }

    @Test
    void replacesAnIdThatCouldMessUpTheLogs() throws Exception {
        AtomicReference<String> mdc = new AtomicReference<>();
        String sneaky = "abc\nFAKE LOG LINE";
        assertThat(run(sneaky, mdc).getHeader("X-Request-Id")).isNotEqualTo(sneaky).hasSize(8);
    }

    @Test
    void cleansUpAfterTheRequest() throws Exception {
        run(null, new AtomicReference<>());
        assertThat(MDC.get("requestId")).isNull();
    }
}
