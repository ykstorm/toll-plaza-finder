package dev.lakshyaraj.tollplazas.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record ErrorResponse(@Schema(example = "Invalid source or destination pincode") String error) {
}
