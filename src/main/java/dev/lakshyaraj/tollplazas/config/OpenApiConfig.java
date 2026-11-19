package dev.lakshyaraj.tollplazas.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("Toll Plaza Finder")
                .version("1.0.0")
                .description("""
                        Finds the toll plazas on the driving route between two Indian pincodes.

                        Pincodes are resolved offline from India Post's pincode directory, the route comes \
                        from OSRM, and plazas are matched against the actual road geometry. \
                        `distanceFromSource` is road distance along the route, not straight-line.

                        Routes are cached in memory and in Postgres. The `X-Route-Source` response header \
                        tells you whether a request hit the cache.""")
                .contact(new Contact().name("Lakshyaraj Singh Rao").url("https://github.com/ykstorm")));
    }
}
