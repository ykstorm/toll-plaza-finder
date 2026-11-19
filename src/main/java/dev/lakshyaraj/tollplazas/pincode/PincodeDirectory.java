package dev.lakshyaraj.tollplazas.pincode;

import dev.lakshyaraj.tollplazas.geo.GeoPoint;

import java.util.Optional;

public interface PincodeDirectory {

    Optional<GeoPoint> locate(Pincode pincode);
}
