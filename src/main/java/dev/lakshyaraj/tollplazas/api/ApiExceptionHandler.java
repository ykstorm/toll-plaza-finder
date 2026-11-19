package dev.lakshyaraj.tollplazas.api;

import dev.lakshyaraj.tollplazas.pincode.InvalidPincodeException;
import dev.lakshyaraj.tollplazas.route.SamePincodeException;
import dev.lakshyaraj.tollplazas.routing.NoRouteFoundException;
import dev.lakshyaraj.tollplazas.routing.RoutingUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// every error ends up as {"error": "..."}. the messages for the spec's cases match it word for word
@RestControllerAdvice
public class ApiExceptionHandler {

    static final String INVALID_PINCODE = "Invalid source or destination pincode";
    static final String SAME_PINCODE = "Source and destination pincodes cannot be the same";
    static final String NO_ROUTE = "No drivable route found between the given pincodes";
    static final String ROUTING_DOWN = "Routing service is temporarily unavailable, please retry";
    static final String MALFORMED_BODY = "Request body must be JSON with sourcePincode and destinationPincode";

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({InvalidPincodeException.class, MethodArgumentNotValidException.class})
    ResponseEntity<ErrorResponse> invalidPincode(Exception e) {
        return error(HttpStatus.BAD_REQUEST, INVALID_PINCODE);
    }

    @ExceptionHandler(SamePincodeException.class)
    ResponseEntity<ErrorResponse> samePincode(SamePincodeException e) {
        return error(HttpStatus.BAD_REQUEST, SAME_PINCODE);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> malformed(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, MALFORMED_BODY);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> mediaType(HttpMediaTypeNotSupportedException e) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type must be application/json");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> method(HttpRequestMethodNotSupportedException e) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "Method " + e.getMethod() + " is not supported here");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> notFound(NoResourceFoundException e) {
        return error(HttpStatus.NOT_FOUND, "Not found");
    }

    @ExceptionHandler(NoRouteFoundException.class)
    ResponseEntity<ErrorResponse> noRoute(NoRouteFoundException e) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, NO_ROUTE);
    }

    @ExceptionHandler(RoutingUnavailableException.class)
    ResponseEntity<ErrorResponse> routingDown(RoutingUnavailableException e) {
        log.warn("Routing provider failure: {}", e.getMessage());
        return error(HttpStatus.SERVICE_UNAVAILABLE, ROUTING_DOWN);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception e) {
        log.error("Unhandled error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side");
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message));
    }
}
