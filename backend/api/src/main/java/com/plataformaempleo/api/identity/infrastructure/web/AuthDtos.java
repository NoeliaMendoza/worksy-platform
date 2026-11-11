package com.plataformaempleo.api.identity.infrastructure.web;

import com.plataformaempleo.api.identity.domain.model.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Contratos JSON de /api/v1/auth. Nunca exponen el hash de la contraseña ni el token de refresco. */
final class AuthDtos {

    private AuthDtos() {
    }

    @Schema(name = "RegisterRequest", description = "Alta pública de una cuenta de candidato")
    record RegisterRequest(
            @Schema(example = "candidata@ejemplo.test") @NotBlank @Size(max = 254) String email,
            @Schema(example = "una-frase-larga-y-segura", minLength = 10, maxLength = 128) @NotBlank String password) {

        @Override
        public String toString() {
            return "RegisterRequest[email=" + email + ", password=***]";
        }
    }

    @Schema(name = "LoginRequest")
    record LoginRequest(
            @Schema(example = "candidata@ejemplo.test") @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password) {

        @Override
        public String toString() {
            return "LoginRequest[email=" + email + ", password=***]";
        }
    }

    @Schema(name = "ForgotPasswordRequest")
    record ForgotPasswordRequest(@Schema(example = "candidata@ejemplo.test") @NotBlank @Size(max = 254) String email) {
    }

    @Schema(name = "ResetPasswordRequest")
    record ResetPasswordRequest(
            @Schema(description = "Valor del parámetro token del enlace recibido por correo") @NotBlank @Size(max = 128) String token,
            @Schema(minLength = 10, maxLength = 128) @NotBlank String password) {

        @Override
        public String toString() {
            return "ResetPasswordRequest[token=***, password=***]";
        }
    }

    @Schema(name = "ProviderResponse", description = "Proveedor externo habilitado para iniciar sesión")
    record ProviderResponse(
            @Schema(example = "google") String id,
            @Schema(example = "Google") String name,
            @Schema(example = "/api/v1/auth/oauth2/authorization/google") String authorizationUrl) {
    }

    @Schema(name = "UserResponse")
    record UserResponse(UUID id, String email, String role, UUID companyId, Instant createdAt) {

        static UserResponse from(User u) {
            return new UserResponse(u.id(), u.email(), u.role().name(), u.companyId(), u.createdAt());
        }
    }

    @Schema(name = "TokenResponse", description = "El token de refresco viaja aparte, en una cookie HttpOnly")
    record TokenResponse(
            @Schema(description = "JWT para la cabecera Authorization: Bearer") String accessToken,
            @Schema(example = "Bearer") String tokenType,
            Instant expiresAt,
            UserResponse user) {
    }
}
