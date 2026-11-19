package dev.lakshyaraj.tollplazas.routing;

import com.github.benmanes.caffeine.cache.AsyncCache;
import dev.lakshyaraj.tollplazas.geo.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

// memory -> postgres -> OSRM.
// it's caffeine's async cache on purpose. the sync one locks a hash bin while the loader runs,
// and the loader is an http call that can take seconds, so unrelated routes would wait on it.
// with async the map holds a future, same-route requests share one OSRM call, nobody else blocks.
// only routes are cached, not toll lists, so a re-imported CSV shows up straight away.
public class RouteCache {

    private static final Logger log = LoggerFactory.getLogger(RouteCache.class);

    private final AsyncCache<RouteKey, Route> memory;
    private final RouteCacheRepository database;
    private final RouteProvider provider;
    private final Duration databaseTtl;

    public RouteCache(AsyncCache<RouteKey, Route> memory, RouteCacheRepository database,
                      RouteProvider provider, Duration databaseTtl) {
        this.memory = memory;
        this.database = database;
        this.provider = provider;
        this.databaseTtl = databaseTtl;
    }

    public CachedRoute get(RouteKey key, RouteRequestPoints points) {
        CompletableFuture<Route> existing = memory.getIfPresent(key);
        if (existing != null) {
            return new CachedRoute(join(key, existing), RouteSource.MEMORY);
        }

        // whoever creates the future records where the route came from. anyone who
        // piggybacks on an in-flight load just sees MEMORY, which is close enough.
        AtomicReference<RouteSource> source = new AtomicReference<>(RouteSource.MEMORY);
        CompletableFuture<Route> future = memory.get(key, (k, executor) -> CompletableFuture.supplyAsync(() -> {
            Optional<Route> stored = findInDatabase(k);
            if (stored.isPresent()) {
                source.set(RouteSource.DATABASE);
                return stored.get();
            }
            Route fresh = provider.route(points.from(), points.to());
            saveToDatabase(k, fresh);
            source.set(RouteSource.PROVIDER);
            return fresh;
        }, executor));

        return new CachedRoute(join(key, future), source.get());
    }

    private Route join(RouteKey key, CompletableFuture<Route> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            // caffeine drops failed futures eventually, but a retry straight after a failure
            // could still get the dead one back. kick it out now so the retry actually retries.
            memory.asMap().remove(key, future);
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }
    }

    // the db is an optimisation. if it's down we still answer, just slower
    private Optional<Route> findInDatabase(RouteKey key) {
        try {
            return database.find(key, databaseTtl);
        } catch (RuntimeException e) {
            log.warn("route cache read failed for {}, going to the provider: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    private void saveToDatabase(RouteKey key, Route route) {
        try {
            database.save(key, route, provider.name());
        } catch (RuntimeException e) {
            log.warn("route cache write failed for {}: {}", key, e.getMessage());
        }
    }

    public record CachedRoute(Route route, RouteSource source) {
    }

    public record RouteRequestPoints(GeoPoint from, GeoPoint to) {
    }
}
