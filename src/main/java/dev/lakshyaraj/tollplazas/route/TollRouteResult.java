package dev.lakshyaraj.tollplazas.route;

import dev.lakshyaraj.tollplazas.matching.MatchedTollPlaza;
import dev.lakshyaraj.tollplazas.pincode.Pincode;
import dev.lakshyaraj.tollplazas.routing.RouteSource;

import java.util.List;

public record TollRouteResult(Pincode source,
                              Pincode destination,
                              double routeDistanceMeters,
                              List<MatchedTollPlaza> tollPlazas,
                              RouteSource routeSource) {
}
