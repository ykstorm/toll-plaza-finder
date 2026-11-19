package dev.lakshyaraj.tollplazas.api;

import dev.lakshyaraj.tollplazas.matching.MatchedTollPlaza;
import dev.lakshyaraj.tollplazas.route.TollRouteResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// same shape as the spec, nothing extra
public record TollPlazaResponse(RouteDto route, List<TollPlazaDto> tollPlazas) {

    public static TollPlazaResponse from(TollRouteResult result) {
        RouteDto route = new RouteDto(result.source().value(), result.destination().value(),
                kilometres(result.routeDistanceMeters()));
        List<TollPlazaDto> plazas = result.tollPlazas().stream().map(TollPlazaResponse::toDto).toList();
        return new TollPlazaResponse(route, plazas);
    }

    private static TollPlazaDto toDto(MatchedTollPlaza m) {
        return new TollPlazaDto(m.plaza().name(), m.plaza().location().latitude(), m.plaza().location().longitude(),
                kilometres(m.distanceFromSourceMeters()));
    }

    static double kilometres(double meters) {
        return Math.round(meters / 100.0) / 10.0; // one decimal place
    }

    public record RouteDto(
            @Schema(example = "560064") String sourcePincode,
            @Schema(example = "411045") String destinationPincode,
            @Schema(description = "Road distance in km", example = "842.3") double distanceInKm) {
    }

    public record TollPlazaDto(
            @Schema(example = "Hoskote Toll Plaza") String name,
            @Schema(example = "13.0789") double latitude,
            @Schema(example = "77.7921") double longitude,
            @Schema(description = "Road distance from the source in km, measured along the route", example = "38.4")
            double distanceFromSource) {
    }
}
