package com.cocab.exception;

/** Incumplimiento de una regla de entrada o negocio con mensaje público seguro. */
public class ValidationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ValidationException(String mensaje) {
        super(mensaje);
    }
}