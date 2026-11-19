package dev.lakshyaraj.tollplazas.api;

import dev.lakshyaraj.tollplazas.route.TollRouteResult;
import dev.lakshyaraj.tollplazas.route.TollRouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/toll-plazas", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Toll plazas")
public class TollPlazaController {

    static final String ROUTE_SOURCE_HEADER = "X-Route-Source";

    private final TollRouteService service;

    public TollPlazaController(TollRouteService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Toll plazas on the driving route between two pincodes",
            description = "Routes from source to destination, then returns every toll plaza the route passes, "
                    + "in the order you reach them. distanceFromSource is road distance along the route.")
    @ApiResponse(responseCode = "200", description = "Route found. tollPlazas can be empty.",
            headers = @Header(name = ROUTE_SOURCE_HEADER,
                    description = "Where the route came from: MEMORY, DATABASE (both cached) or PROVIDER"))
    @ApiResponse(responseCode = "400", description = "Bad, unknown or identical pincodes",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                    @ExampleObject(name = "invalid", value = "{\"error\": \"Invalid source or destination pincode\"}"),
                    @ExampleObject(name = "same", value = "{\"error\": \"Source and destination pincodes cannot be the same\"}")}))
    @ApiResponse(responseCode = "422", description = "No drivable route between the pincodes, e.g. an island",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Routing provider down or timed out",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<TollPlazaResponse> findTollPlazas(@Valid @RequestBody TollPlazaRequest request) {
        TollRouteResult result = service.findTollPlazas(request.sourcePincode(), request.destinationPincode());
        return ResponseEntity.ok()
                .header(ROUTE_SOURCE_HEADER, result.routeSource().name())
                .body(TollPlazaResponse.from(result));
    }
}
