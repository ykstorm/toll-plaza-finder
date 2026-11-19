package dev.lakshyaraj.tollplazas.routing;

import dev.lakshyaraj.tollplazas.geo.GeoPoint;

// OSRM by default because it needs no API key. Mappls or Google would be one more class
public interface RouteProvider {

    // throws NoRouteFoundException if there's no road, RoutingUnavailableException if it's down
    Route route(GeoPoint from, GeoPoint to);

    String name();
}
