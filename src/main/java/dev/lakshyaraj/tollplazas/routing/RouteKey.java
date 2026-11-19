package dev.lakshyaraj.tollplazas.routing;

import dev.lakshyaraj.tollplazas.pincode.Pincode;

// A->B and B->A are cached separately, one-ways and divided highways can route differently
public record RouteKey(Pincode source, Pincode destination) {
}
