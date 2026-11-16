package com.plataformaempleo.api.identity.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Datos de la cuenta para administración; nunca incluye credenciales ni datos del CV. */
public record UserOverview(UUID id, String email, Role role, UUID companyId, String companyName, Instant createdAt) { }
