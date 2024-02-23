package com.inmobi.supply.sun.exception;

public class InvalidMessageException extends Exception {
    public InvalidMessageException() {

    }

    public InvalidMessageException(String exception) {
        super(exception);
    }

    public InvalidMessageException(String exception, Throwable cause) {
        super(exception, cause);
    }

    public InvalidMessageException(Throwable cause) {
        super(cause);
    }
}