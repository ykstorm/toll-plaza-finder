package dev.lakshyaraj.tollplazas.route;

import dev.lakshyaraj.tollplazas.geo.GeoPoint;
import dev.lakshyaraj.tollplazas.geo.RoutePath;
import dev.lakshyaraj.tollplazas.matching.MatchedTollPlaza;
import dev.lakshyaraj.tollplazas.matching.TollPlazaMatcher;
import dev.lakshyaraj.tollplazas.pincode.InvalidPincodeException;
import dev.lakshyaraj.tollplazas.pincode.Pincode;
import dev.lakshyaraj.tollplazas.pincode.PincodeDirectory;
import dev.lakshyaraj.tollplazas.routing.RouteCache;
import dev.lakshyaraj.tollplazas.routing.RouteKey;
import dev.lakshyaraj.tollplazas.tollplaza.TollPlaza;
import dev.lakshyaraj.tollplazas.tollplaza.TollPlazaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

// validate -> pincodes to coords -> route (cached) -> plazas near the route -> match
public class TollRouteService {

    private static final Logger log = LoggerFactory.getLogger(TollRouteService.class);

    private final PincodeDirectory pincodes;
    private final RouteCache routes;
    private final TollPlazaRepository tollPlazas;
    private final TollPlazaMatcher matcher;
    private final double corridorMeters;

    public TollRouteService(PincodeDirectory pincodes, RouteCache routes, TollPlazaRepository tollPlazas,
                            TollPlazaMatcher matcher, double corridorMeters) {
        this.pincodes = pincodes;
        this.routes = routes;
        this.tollPlazas = tollPlazas;
        this.matcher = matcher;
        this.corridorMeters = corridorMeters;
    }

    public TollRouteResult findTollPlazas(String rawSource, String rawDestination) {
        Pincode source = Pincode.of(rawSource);
        Pincode destination = Pincode.of(rawDestination);
        if (source.equals(destination)) {
            throw new SamePincodeException(source.value());
        }

        GeoPoint from = locate(source);
        GeoPoint to = locate(destination);

        RouteCache.CachedRoute cached = routes.get(new RouteKey(source, destination),
                new RouteCache.RouteRequestPoints(from, to));
        RoutePath path = cached.route().path();

        List<TollPlaza> candidates = tollPlazas.findWithin(path.boundingBox().expandedBy(corridorMeters));
        List<MatchedTollPlaza> matched = matcher.match(path, candidates);

        log.info("{} -> {}: {} km, {} candidate plazas, {} on route, route from {}",
                source, destination, Math.round(cached.route().distanceMeters() / 1000),
                candidates.size(), matched.size(), cached.source());

        return new TollRouteResult(source, destination, cached.route().distanceMeters(), matched, cached.source());
    }

    private GeoPoint locate(Pincode pincode) {
        return pincodes.locate(pincode).orElseThrow(() -> new InvalidPincodeException(pincode.value()));
    }
}
