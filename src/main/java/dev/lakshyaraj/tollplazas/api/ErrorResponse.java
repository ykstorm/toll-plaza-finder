package dev.lakshyaraj.tollplazas.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record ErrorResponse(@Schema(description = "What went wrong, in plain words") String error) {
}
