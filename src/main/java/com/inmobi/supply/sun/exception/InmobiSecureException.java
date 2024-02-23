package com.inmobi.supply.sun.exception;

public class InmobiSecureException extends RuntimeException {
    public InmobiSecureException() {
    }

    public InmobiSecureException(String exception) {
        super(exception);
    }

    public InmobiSecureException(String exception, Throwable cause) {
        super(exception,cause);
    }

    public InmobiSecureException(Throwable cause) {
        super(cause);
    }
}