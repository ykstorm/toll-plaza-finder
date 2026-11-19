package dev.lakshyaraj.tollplazas.routing;

// provider worked fine, there just isn't a road (Port Blair, Lakshadweep)
public class NoRouteFoundException extends RuntimeException {

    public NoRouteFoundException(String message) {
        super(message);
    }
}
