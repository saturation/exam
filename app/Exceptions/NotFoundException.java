package Exceptions;

public class NotFoundException extends SitnetException {
    public NotFoundException() {
    }

    public NotFoundException(String message) {
        super(message);
    }
}