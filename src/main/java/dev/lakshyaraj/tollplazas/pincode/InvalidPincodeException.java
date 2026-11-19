package dev.lakshyaraj.tollplazas.pincode;

public class InvalidPincodeException extends RuntimeException {

    private final String pincode;

    public InvalidPincodeException(String pincode) {
        super("Invalid pincode: " + pincode);
        this.pincode = pincode;
    }

    public String pincode() {
        return pincode;
    }
}
