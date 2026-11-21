package dev.lakshyaraj.tollplazas;

import com.github.benmanes.caffeine.cache.AsyncCache;
import dev.lakshyaraj.tollplazas.geo.GeoPoint;
import dev.lakshyaraj.tollplazas.routing.RouteKey;
import dev.lakshyaraj.tollplazas.routing.Route;
import dev.lakshyaraj.tollplazas.routing.RouteProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// full stack: real Flyway schema, real CSV import, real pincode directory, real matching.
// Only the routing provider is faked, with a road that follows NH48 out of Bengaluru past
// the real Nelamangala and Kulumapalya plaza coordinates from the assignment CSV.
@SpringBootTest
@AutoConfigureMockMvc
class TollPlazaApiIT extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AsyncCache<RouteKey, Route> memoryCache;
    @MockitoBean
    private RouteProvider routeProvider;

    @BeforeEach
    void setUp() {
        jdbc.update("TRUNCATE route_cache");
        memoryCache.synchronous().invalidateAll();
        when(routeProvider.name()).thenReturn("fake");
    }

    private ResultActions request(String source, String destination) throws Exception {
        return mvc.perform(post("/api/v1/toll-plazas").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourcePincode\":\"" + source + "\",\"destinationPincode\":\"" + destination + "\"}"));
    }

    // Yelahanka -> Nelamangala -> Kulumapalya -> towards Tumkur, densified every ~100 m.
    private static Route nh48OutOfBengaluru() {
        List<GeoPoint> waypoints = List.of(
                GeoPoint.of(13.107, 77.5688),
                GeoPoint.of(13.0920, 77.4000),   // just past the Banglaore-Nelamangala booth
                GeoPoint.of(13.1149, 77.3751),   // Kulumapalya
                GeoPoint.of(13.3020, 77.1620));  // Chokkenahalli
        List<GeoPoint> dense = new ArrayList<>();
        for (int i = 0; i < waypoints.size() - 1; i++) {
            GeoPoint a = waypoints.get(i);
            GeoPoint b = waypoints.get(i + 1);
            for (int s = 0; s < 100; s++) {
                double t = s / 100.0;
                dense.add(GeoPoint.of(a.latitude() + t * (b.latitude() - a.latitude()),
                        a.longitude() + t * (b.longitude() - a.longitude())));
            }
        }
        dense.add(waypoints.get(waypoints.size() - 1));
        return Route.of(62_000, dense);
    }

    @Test
    void endToEndAgainstTheRealData() throws Exception {
        when(routeProvider.route(any(), any())).thenReturn(nh48OutOfBengaluru());

        request("560064", "572101")
                .andExpect(status().isOk())
                .andExpect(header().string("X-Route-Source", "PROVIDER"))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.route.distanceInKm").value(62.0))
                // two Nelamangala plazas 640 m apart, only the one nearest the road should come back
                .andExpect(jsonPath("$.tollPlazas.length()").value(3))
                .andExpect(jsonPath("$.tollPlazas[1].name").value("Kulumapalya Toll Plaza"))
                .andExpect(jsonPath("$.tollPlazas[2].name").value("Chokkenahalli Toll Plaza"));
    }

    @Test
    void secondRequestIsServedFromCacheWithoutCallingTheProvider() throws Exception {
        when(routeProvider.route(any(), any())).thenReturn(nh48OutOfBengaluru());

        request("560064", "572101").andExpect(header().string("X-Route-Source", "PROVIDER"));
        request("560064", "572101").andExpect(header().string("X-Route-Source", "MEMORY"));

        verify(routeProvider, times(1)).route(any(), any());
    }

    @Test
    void unknownPincodeNeverReachesTheProvider() throws Exception {
        request("560064", "999999")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid source or destination pincode"));
        verify(routeProvider, times(0)).route(any(), any());
    }

    @Test
    void rootRedirectsToSwaggerAndDocsAreServed() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/swagger-ui.html"));
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/toll-plazas'].post").exists());
    }

    @Test
    void healthIsUp() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
