package dev.lakshyaraj.tollplazas.routing;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.lakshyaraj.tollplazas.geo.GeoPoint;
import dev.lakshyaraj.tollplazas.pincode.Pincode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteCacheTest {

    private static final Duration TTL = Duration.ofDays(30);
    private static final RouteKey KEY = new RouteKey(Pincode.of("560064"), Pincode.of("411045"));
    private static final GeoPoint FROM = GeoPoint.of(13.107, 77.5688);
    private static final GeoPoint TO = GeoPoint.of(18.57, 73.7662);
    private static final RouteCache.RouteRequestPoints POINTS = new RouteCache.RouteRequestPoints(FROM, TO);
    private static final Route ROUTE = Route.of(842_000, List.of(FROM, TO));

    @Mock
    private RouteCacheRepository database;
    @Mock
    private RouteProvider provider;

    private AsyncCache<RouteKey, Route> memory;
    private RouteCache cache;

    @BeforeEach
    void setUp() {
        memory = Caffeine.newBuilder().buildAsync();
        cache = new RouteCache(memory, database, provider, TTL);
    }

    @Test
    void firstCallGoesToProviderAndIsPersisted() {
        when(database.find(KEY, TTL)).thenReturn(Optional.empty());
        when(provider.route(FROM, TO)).thenReturn(ROUTE);
        when(provider.name()).thenReturn("osrm");

        RouteCache.CachedRoute result = cache.get(KEY, POINTS);

        assertThat(result.source()).isEqualTo(RouteSource.PROVIDER);
        assertThat(result.route()).isEqualTo(ROUTE);
        verify(database).save(KEY, ROUTE, "osrm");
    }

    @Test
    void secondCallIsServedFromMemory() {
        when(database.find(KEY, TTL)).thenReturn(Optional.empty());
        when(provider.route(FROM, TO)).thenReturn(ROUTE);
        when(provider.name()).thenReturn("osrm");

        cache.get(KEY, POINTS);
        RouteCache.CachedRoute second = cache.get(KEY, POINTS);

        assertThat(second.source()).isEqualTo(RouteSource.MEMORY);
        verify(provider, times(1)).route(any(), any());
    }

    @Test
    void afterRestartTheDatabaseServesTheRoute() {
        when(database.find(KEY, TTL)).thenReturn(Optional.of(ROUTE));

        RouteCache.CachedRoute result = cache.get(KEY, POINTS);

        assertThat(result.source()).isEqualTo(RouteSource.DATABASE);
        verify(provider, never()).route(any(), any());
        assertThat(memory.synchronous().getIfPresent(KEY)).isEqualTo(ROUTE); // warmed for next time
    }

    @Test
    void databaseOutageDoesNotBreakRequests() {
        when(database.find(KEY, TTL)).thenThrow(new RuntimeException("connection refused"));
        when(provider.route(FROM, TO)).thenReturn(ROUTE);
        when(provider.name()).thenReturn("osrm");
        org.mockito.Mockito.doThrow(new RuntimeException("connection refused"))
                .when(database).save(eq(KEY), eq(ROUTE), anyString());

        assertThat(cache.get(KEY, POINTS).source()).isEqualTo(RouteSource.PROVIDER);
    }

    @Test
    void providerFailuresAreNotCached() {
        when(database.find(KEY, TTL)).thenReturn(Optional.empty());
        when(provider.route(FROM, TO))
                .thenThrow(new RoutingUnavailableException("down"))
                .thenReturn(ROUTE);
        when(provider.name()).thenReturn("osrm");

        assertThatThrownBy(() -> cache.get(KEY, POINTS)).isInstanceOf(RoutingUnavailableException.class);
        assertThat(cache.get(KEY, POINTS).source()).isEqualTo(RouteSource.PROVIDER);
    }

    @Test
    void concurrentRequestsForTheSameRouteCallTheProviderOnce() throws Exception {
        CountDownLatch providerEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(database.find(KEY, TTL)).thenReturn(Optional.empty());
        when(provider.name()).thenReturn("osrm");
        when(provider.route(FROM, TO)).thenAnswer(inv -> {
            providerEntered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return ROUTE;
        });

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            Future<RouteCache.CachedRoute> first = pool.submit(() -> cache.get(KEY, POINTS));
            assertThat(providerEntered.await(5, TimeUnit.SECONDS)).isTrue();
            List<Future<RouteCache.CachedRoute>> others = new java.util.ArrayList<>();
            for (int i = 0; i < 7; i++) {
                others.add(pool.submit(() -> cache.get(KEY, POINTS)));
            }
            release.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS).route()).isEqualTo(ROUTE);
            for (Future<RouteCache.CachedRoute> f : others) {
                assertThat(f.get(5, TimeUnit.SECONDS).route()).isEqualTo(ROUTE);
            }
        } finally {
            pool.shutdownNow();
        }
        verify(provider, times(1)).route(FROM, TO);
    }
}
