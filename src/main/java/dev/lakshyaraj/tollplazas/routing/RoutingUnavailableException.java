package dev.lakshyaraj.tollplazas.routing;

// provider down, timed out or sent junk. maps to a 503
public class RoutingUnavailableException extends RuntimeException {

    public RoutingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public RoutingUnavailableException(String message) {
        super(message);
    }
}
