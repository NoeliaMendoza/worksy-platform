package com.plataformaempleo.api.identity.infrastructure.web;

import com.plataformaempleo.api.identity.application.AuthService;
import com.plataformaempleo.api.identity.application.AuthService.AuthSession;
import com.plataformaempleo.api.identity.application.PasswordResetService;
import com.plataformaempleo.api.identity.infrastructure.security.OAuthClients;
import com.plataformaempleo.api.identity.infrastructure.web.AuthDtos.ForgotPasswordRequest;
import com.plataformaempleo.api.identity.infrastructure.web.AuthDtos.LoginRequest;
import com.plataformaempleo.api.identity.infrastructure.web.AuthDtos.ProviderResponse;
import com.plataformaempleo.api.identity.infrastructure.web.AuthDtos.RegisterRequest;
import com.plataformaempleo.api.identity.infrastructure.web.AuthDtos.ResetPasswordRequest;
import com.plataformaempleo.api.identity.infrastructure.web.AuthDtos.TokenResponse;
import com.plataformaempleo.api.identity.infrastructure.web.AuthDtos.UserResponse;
import com.plataformaempleo.api.shared.infrastructure.web.ApiExceptionHandler;
import com.plataformaempleo.common.domain.DomainException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Adaptador de entrada REST para la autenticación. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación", description = "Registro, inicio de sesión, recuperación de contraseña y tokens")
class AuthController {

    private final AuthService auth;
    private final PasswordResetService passwordReset;
    private final OAuthClients oauthClients;
    private final RefreshCookies cookies;

    AuthController(AuthService auth, PasswordResetService passwordReset, OAuthClients oauthClients,
                   RefreshCookies cookies) {
        this.auth = auth;
        this.passwordReset = passwordReset;
        this.oauthClients = oauthClients;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    @Operation(summary = "Crear una cuenta de candidato")
    @ApiResponse(responseCode = "201", description = "Cuenta creada")
    @ApiResponse(responseCode = "400", description = "Correo o contraseña no válidos")
    @ApiResponse(responseCode = "409", description = "Ya existe una cuenta con ese correo")
    ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest body) {
        var user = auth.registerCandidate(body.email(), body.password());
        return ResponseEntity.created(URI.create("/api/v1/auth/me")).body(UserResponse.from(user));
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión", description = "Devuelve el token de acceso y deja el de refresco en una cookie HttpOnly")
    @ApiResponse(responseCode = "200", description = "Sesión iniciada")
    @ApiResponse(responseCode = "401", description = "Correo o contraseña incorrectos")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest body) {
        return withSession(auth.login(body.email(), body.password()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar el token de acceso",
            description = "Rota el token de refresco. Reutilizar uno ya rotado revoca toda la sesión.")
    @ApiResponse(responseCode = "200", description = "Tokens renovados")
    @ApiResponse(responseCode = "401", description = "Token de refresco ausente, caducado, revocado o reutilizado")
    ResponseEntity<TokenResponse> refresh(
            @Parameter(hidden = true) @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken) {
        try {
            return withSession(auth.refresh(refreshToken));
        } catch (RuntimeException e) {
            // Si el refresco falla, se borra la cookie para que el cliente no la reintente.
            throw new CookieClearingException(e, cookies.cleared());
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Cerrar sesión", description = "Revoca la sesión del token de refresco y borra la cookie")
    @ApiResponse(responseCode = "204", description = "Sesión cerrada")
    ResponseEntity<Void> logout(
            @Parameter(hidden = true) @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken) {
        auth.logout(refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.cleared().toString()).build();
    }

    @PostMapping("/password/forgot")
    @Operation(summary = "Pedir un enlace para restablecer la contraseña",
            description = "Responde 202 siempre, exista o no la cuenta. Si existe, envía un correo con un enlace de un solo uso.")
    @ApiResponse(responseCode = "202", description = "Solicitud recibida")
    ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest body) {
        passwordReset.requestReset(body.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password/reset")
    @Operation(summary = "Elegir una contraseña nueva con el enlace del correo",
            description = "El enlace sirve una sola vez. Al cambiar la contraseña se cierran todas las sesiones abiertas.")
    @ApiResponse(responseCode = "204", description = "Contraseña cambiada")
    @ApiResponse(responseCode = "400", description = "Enlace caducado o ya usado, o contraseña no válida")
    ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest body) {
        passwordReset.resetPassword(body.token(), body.password());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/providers")
    @Operation(summary = "Proveedores externos habilitados",
            description = "Para iniciar sesión con uno, el navegador va a la URL de authorizationUrl.")
    List<ProviderResponse> providers() {
        return oauthClients.enabled().stream()
                .map(p -> new ProviderResponse(p.getKey(), p.getValue(),
                        OAuthClients.AUTHORIZATION_BASE + "/" + p.getKey()))
                .toList();
    }

    @GetMapping("/me")
    @Operation(summary = "Usuario actual", security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "Datos del usuario autenticado")
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido")
    UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(auth.currentUser(UUID.fromString(jwt.getSubject())));
    }

    private ResponseEntity<TokenResponse> withSession(AuthSession s) {
        TokenResponse body = new TokenResponse(s.accessToken().value(), "Bearer", s.accessToken().expiresAt(),
                UserResponse.from(s.user()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.forSession(s).toString())
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @ExceptionHandler(CookieClearingException.class)
    ResponseEntity<ProblemDetail> refreshFailed(CookieClearingException e) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, e.cookie().toString());
        if (e.getCause() instanceof DomainException domain) {
            return ApiExceptionHandler.toResponse(domain, headers);
        }
        throw (RuntimeException) e.getCause();
    }

    /** Envuelve un fallo de refresco para que el manejador de errores también borre la cookie. */
    static final class CookieClearingException extends RuntimeException {

        private final transient ResponseCookie cookie;

        CookieClearingException(RuntimeException cause, ResponseCookie cookie) {
            super(cause);
            this.cookie = cookie;
        }

        ResponseCookie cookie() {
            return cookie;
        }
    }
}
