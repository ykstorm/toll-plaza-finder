package dev.lakshyaraj.tollplazas.api;

import dev.lakshyaraj.tollplazas.config.TollPlazaProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.time.Clock;

// request id goes first so even a rate-limited request gets an id in the logs
@Configuration
public class WebFilterConfig {

    @Bean
    FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> bean = new FilterRegistrationBean<>(new RequestIdFilter());
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return bean;
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(TollPlazaProperties props) {
        FilterRegistrationBean<RateLimitFilter> bean = new FilterRegistrationBean<>(
                new RateLimitFilter(props.rateLimit().requestsPerMinute(), Clock.systemUTC()));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return bean;
    }
}
