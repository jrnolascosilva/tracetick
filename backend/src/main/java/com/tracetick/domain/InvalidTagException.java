package com.tracetick.domain;

public class InvalidTagException extends RuntimeException {

    public InvalidTagException(String message) {
        super(message);
    }
}
