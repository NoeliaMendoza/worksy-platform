package com.plataformaempleo.api.identity.infrastructure.security;

import com.plataformaempleo.api.identity.application.AuthService.AuthSession;
import com.plataformaempleo.api.identity.application.ExternalLoginService;
import com.plataformaempleo.api.identity.application.ExternalLoginService.ExternalProfile;
import com.plataformaempleo.api.identity.infrastructure.web.RefreshCookies;
import com.plataformaempleo.common.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * Regreso del proveedor externo. Si todo va bien, abre una sesión de Worksy (misma cookie de refresco
 * que el login con contraseña) y vuelve al frontend, que recupera la sesión con /refresh.
 * Si falla, vuelve al frontend con {@code ?acceso=<motivo>} para mostrar un aviso.
 */
class OAuthLoginHandlers implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuthLoginHandlers.class);

    private final ExternalLoginService externalLogin;
    private final RefreshCookies cookies;
    private final String publicUrl;

    OAuthLoginHandlers(ExternalLoginService externalLogin, RefreshCookies cookies, String publicUrl) {
        this.externalLogin = externalLogin;
        this.cookies = cookies;
        this.publicUrl = publicUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        endTemporarySession(request);
        try {
            AuthSession session = externalLogin.login(profileOf((OAuth2AuthenticationToken) authentication));
            response.addHeader(HttpHeaders.SET_COOKIE, cookies.forSession(session).toString());
            response.sendRedirect(publicUrl + "/");
        } catch (DomainException e) {
            String reason = "auth.external.email-unverified".equals(e.code()) ? "correo-no-verificado" : "error";
            response.sendRedirect(publicUrl + "/?acceso=" + reason);
        }
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        endTemporarySession(request);
        String reason = "error";
        if (exception instanceof OAuth2AuthenticationException oauth
                && "access_denied".equals(oauth.getError().getErrorCode())) {
            reason = "cancelado";
        } else {
            log.warn("Falló el inicio de sesión externo: {}", exception.getClass().getSimpleName());
        }
        response.sendRedirect(publicUrl + "/?acceso=" + reason);
    }

    static ExternalProfile profileOf(OAuth2AuthenticationToken token) {
        String provider = token.getAuthorizedClientRegistrationId();
        OAuth2User principal = token.getPrincipal();
        if (principal instanceof OidcUser oidc) {
            return new ExternalProfile(provider, oidc.getSubject(), oidc.getEmail(),
                    Boolean.TRUE.equals(oidc.getEmailVerified()));
        }
        Object email = principal.getAttribute("email");
        return new ExternalProfile(provider, principal.getName(), email == null ? null : email.toString(),
                Boolean.TRUE.equals(principal.getAttribute("email_verified")));
    }

    /** La sesión HTTP solo guardaba el "state" de OAuth durante la ida y vuelta: se descarta al terminar. */
    private static void endTemporarySession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
