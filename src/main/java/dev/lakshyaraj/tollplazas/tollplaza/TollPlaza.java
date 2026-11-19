package dev.lakshyaraj.tollplazas.tollplaza;

import dev.lakshyaraj.tollplazas.geo.GeoPoint;

public record TollPlaza(long id, String name, String state, GeoPoint location) {
}
