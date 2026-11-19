package dev.lakshyaraj.tollplazas.routing;

// sent back as the X-Route-Source header so you can actually see the cache working
public enum RouteSource {
    MEMORY,
    DATABASE,
    PROVIDER
}
