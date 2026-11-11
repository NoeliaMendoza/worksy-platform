package com.plataformaempleo.api.identity.infrastructure.web;

import com.plataformaempleo.api.identity.application.AuthService.AuthSession;
import com.plataformaempleo.api.identity.infrastructure.security.AuthProperties;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie del token de refresco: HttpOnly, SameSite=Strict y limitada a {@code /api/v1/auth}.
 * La usan el controlador de autenticación y el regreso del inicio de sesión externo.
 */
@Component
public class RefreshCookies {

    public static final String NAME = "worksy_refresh";
    public static final String PATH = "/api/v1/auth";

    private final AuthProperties props;

    RefreshCookies(AuthProperties props) {
        this.props = props;
    }

    public ResponseCookie forSession(AuthSession session) {
        Duration maxAge = Duration.between(Instant.now(), session.refreshExpiresAt());
        return base(session.refreshSecret()).maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge).build();
    }

    public ResponseCookie cleared() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(props.refreshCookieSecure())
                .sameSite("Strict")
                .path(PATH);
    }
}
