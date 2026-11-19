package dev.lakshyaraj.tollplazas.routing.osrm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.lakshyaraj.tollplazas.geo.GeoPoint;
import dev.lakshyaraj.tollplazas.routing.NoRouteFoundException;
import dev.lakshyaraj.tollplazas.routing.Route;
import dev.lakshyaraj.tollplazas.routing.RouteProvider;
import dev.lakshyaraj.tollplazas.routing.RoutingUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Locale;
import java.util.Set;

// http://project-osrm.org/docs/v5.24.0/api/#route-service
// full geometry, not the simplified overview, otherwise plazas on curves get missed
public class OsrmRouteProvider implements RouteProvider {

    private static final Logger log = LoggerFactory.getLogger(OsrmRouteProvider.class);

    // OSRM's way of saying "these points aren't connected by road" or "can't snap to a road"
    private static final Set<String> NO_ROUTE_CODES = Set.of("NoRoute", "NoSegment");

    private final RestClient restClient;
    private final double maxSnapMeters;

    // OSRM snaps to the nearest road no matter how far. Port Blair got snapped ~494 km
    // away onto the Myanmar coast and came back as a 3,562 km "route". so if a point
    // had to move more than maxSnapMeters, there's no road.
    public OsrmRouteProvider(RestClient restClient, double maxSnapMeters) {
        this.restClient = restClient;
        this.maxSnapMeters = maxSnapMeters;
    }

    @Override
    public Route route(GeoPoint from, GeoPoint to) {
        // OSRM wants lon,lat. Getting this backwards still returns a route, just a very wrong one.
        String coordinates = String.format(Locale.ROOT, "%.6f,%.6f;%.6f,%.6f",
                from.longitude(), from.latitude(), to.longitude(), to.latitude());

        OsrmResponse response;
        try {
            response = restClient.get()
                    // not a {template} var, that percent-encodes the , and ; and OSRM chokes on it
                    .uri(builder -> builder.path("/route/v1/driving/" + coordinates)
                            .queryParam("overview", "full")
                            .queryParam("geometries", "polyline6")
                            .queryParam("alternatives", "false")
                            .queryParam("steps", "false")
                            .build())
                    .exchange((request, res) -> {
                        if (res.getStatusCode().is5xxServerError()) {
                            throw new RoutingUnavailableException("OSRM returned " + res.getStatusCode());
                        }
                        // 4xx still carries a JSON body with a code, e.g. {"code":"NoRoute"}
                        return res.bodyTo(OsrmResponse.class);
                    });
        } catch (RestClientException e) {
            throw new RoutingUnavailableException("OSRM request failed: " + e.getMessage(), e);
        }

        if (response == null || response.code() == null) {
            throw new RoutingUnavailableException("OSRM returned an empty response");
        }
        if (NO_ROUTE_CODES.contains(response.code())) {
            throw new NoRouteFoundException("No drivable route found (" + response.code() + ")");
        }
        if (!"Ok".equals(response.code()) || response.routes() == null || response.routes().isEmpty()) {
            log.warn("Unexpected OSRM response code={} message={}", response.code(), response.message());
            throw new RoutingUnavailableException("OSRM could not route: " + response.code());
        }

        if (response.waypoints() != null) {
            for (OsrmWaypoint waypoint : response.waypoints()) {
                if (waypoint.distance() > maxSnapMeters) {
                    throw new NoRouteFoundException(String.format(Locale.ROOT,
                            "Nearest road is %.0f km from the pincode", waypoint.distance() / 1000));
                }
            }
        }

        OsrmRoute best = response.routes().get(0);
        return new Route(best.distance(), best.geometry());
    }

    @Override
    public String name() {
        return "osrm";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OsrmResponse(String code, String message, List<OsrmRoute> routes, List<OsrmWaypoint> waypoints) {
    }

    // distance = how far OSRM moved the point to get it onto a road
    @JsonIgnoreProperties(ignoreUnknown = true)
    record OsrmWaypoint(double distance) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OsrmRoute(double distance, double duration, String geometry) {
    }
}
