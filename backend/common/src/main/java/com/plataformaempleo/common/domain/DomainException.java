package com.plataformaempleo.common.domain;

/**
 * Violación de una regla de negocio. Los adaptadores web la traducen a ProblemDetail.
 */
public class DomainException extends RuntimeException {

    private final String code;

    public DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
