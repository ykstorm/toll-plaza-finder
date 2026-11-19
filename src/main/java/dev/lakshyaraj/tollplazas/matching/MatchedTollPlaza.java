package dev.lakshyaraj.tollplazas.matching;

import dev.lakshyaraj.tollplazas.tollplaza.TollPlaza;

// offsetMeters = how far the plaza's coordinate is from the road
public record MatchedTollPlaza(TollPlaza plaza, double distanceFromSourceMeters, double offsetMeters) {
}
