package com.plataformaempleo.api.identity.domain.model;

public enum Role {
    CANDIDATE,
    RECRUITER,
    COMPANY_ADMIN,
    PLATFORM_ADMIN;

    /** Los roles de empresa deben pertenecer a una empresa (inquilino). */
    public boolean requiresCompany() {
        return this == RECRUITER || this == COMPANY_ADMIN;
    }
}
