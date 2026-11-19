package dev.lakshyaraj.tollplazas.config;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.lakshyaraj.tollplazas.matching.TollPlazaMatcher;
import dev.lakshyaraj.tollplazas.pincode.CsvPincodeDirectory;
import dev.lakshyaraj.tollplazas.pincode.PincodeDirectory;
import dev.lakshyaraj.tollplazas.route.TollRouteService;
import dev.lakshyaraj.tollplazas.routing.Route;
import dev.lakshyaraj.tollplazas.routing.RouteCache;
import dev.lakshyaraj.tollplazas.routing.RouteCacheRepository;
import dev.lakshyaraj.tollplazas.routing.RouteKey;
import dev.lakshyaraj.tollplazas.routing.RouteProvider;
import dev.lakshyaraj.tollplazas.routing.osrm.OsrmRouteProvider;
import dev.lakshyaraj.tollplazas.tollplaza.TollPlazaCsvParser;
import dev.lakshyaraj.tollplazas.tollplaza.TollPlazaImporter;
import dev.lakshyaraj.tollplazas.tollplaza.TollPlazaRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

// all the wiring is here so the domain classes don't need spring annotations
@Configuration
@EnableConfigurationProperties(TollPlazaProperties.class)
public class AppConfig {

    @Bean
    PincodeDirectory pincodeDirectory(TollPlazaProperties props) {
        return new CsvPincodeDirectory(props.data().pincodeCsv());
    }

    @Bean
    RouteProvider osrmRouteProvider(TollPlazaProperties props, RestClient.Builder builder) {
        TollPlazaProperties.Routing routing = props.routing();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(routing.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(routing.readTimeout());

        RestClient client = builder
                .baseUrl(routing.osrmBaseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, routing.userAgent())
                .build();
        return new OsrmRouteProvider(client, routing.maxSnapMeters());
    }

    @Bean
    AsyncCache<RouteKey, Route> routeMemoryCache(TollPlazaProperties props) {
        return Caffeine.newBuilder()
                .maximumSize(props.cache().memoryMaxEntries())
                .expireAfterWrite(props.cache().memoryTtl())
                .buildAsync();
    }

    @Bean
    RouteCacheRepository routeCacheRepository(JdbcClient jdbcClient) {
        return new RouteCacheRepository(jdbcClient);
    }

    @Bean
    RouteCache routeCache(AsyncCache<RouteKey, Route> memory, RouteCacheRepository repository,
                          RouteProvider provider, TollPlazaProperties props) {
        return new RouteCache(memory, repository, provider, props.cache().databaseTtl());
    }

    @Bean
    TollPlazaRepository tollPlazaRepository(JdbcTemplate jdbcTemplate) {
        return new TollPlazaRepository(jdbcTemplate);
    }

    @Bean
    TollPlazaImporter tollPlazaImporter(TollPlazaRepository repository) {
        return new TollPlazaImporter(new TollPlazaCsvParser(), repository);
    }

    @Bean
    ApplicationRunner importTollPlazasOnStartup(TollPlazaImporter importer, TollPlazaProperties props) {
        return args -> {
            if (props.data().importOnStartup()) {
                importer.importFrom(props.data().tollPlazaCsv());
            }
        };
    }

    @Bean
    TollPlazaMatcher tollPlazaMatcher(TollPlazaProperties props) {
        return new TollPlazaMatcher(props.matching().corridorMeters(), props.matching().duplicateWindowMeters(),
                props.matching().sameSiteMeters());
    }

    @Bean
    TollRouteService tollRouteService(PincodeDirectory pincodes, RouteCache routes, TollPlazaRepository plazas,
                                      TollPlazaMatcher matcher, TollPlazaProperties props) {
        return new TollRouteService(pincodes, routes, plazas, matcher, props.matching().corridorMeters());
    }
}
