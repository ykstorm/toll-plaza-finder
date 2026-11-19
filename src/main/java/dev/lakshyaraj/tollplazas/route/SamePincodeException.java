package dev.lakshyaraj.tollplazas.route;

public class SamePincodeException extends RuntimeException {

    public SamePincodeException(String pincode) {
        super("Source and destination are both " + pincode);
    }
}
