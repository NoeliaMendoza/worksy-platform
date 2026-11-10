package com.plataformaempleo.api.identity.infrastructure.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;

/**
 * Registro de los proveedores habilitados. Puede estar vacío: entonces no se activa el login externo.
 * Las URL de LinkedIn son las de su documentación de "Sign In with LinkedIn using OpenID Connect".
 */
public class OAuthClients implements ClientRegistrationRepository {

    public static final String AUTHORIZATION_BASE = "/api/v1/auth/oauth2/authorization";
    public static final String CALLBACK_BASE = "/api/v1/auth/oauth2/callback";
    private static final String REDIRECT = "{baseUrl}" + CALLBACK_BASE + "/{registrationId}";

    /** Nombre visible de cada proveedor, en el orden en que se muestran. */
    static final Map<String, String> DISPLAY_NAMES = new LinkedHashMap<>(Map.of());

    static {
        DISPLAY_NAMES.put("google", "Google");
        DISPLAY_NAMES.put("linkedin", "LinkedIn");
        DISPLAY_NAMES.put("github", "GitHub");
    }

    private final Map<String, ClientRegistration> registrations = new LinkedHashMap<>();

    public OAuthClients(OAuthProperties props) {
        DISPLAY_NAMES.keySet().forEach(id -> {
            OAuthProperties.Client client = props.providers().get(id);
            if (client != null && client.isConfigured()) {
                registrations.put(id, build(id, client));
            }
        });
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        return registrations.get(registrationId);
    }

    public boolean isEmpty() {
        return registrations.isEmpty();
    }

    /** Proveedores habilitados: id y nombre visible. */
    public List<Map.Entry<String, String>> enabled() {
        return registrations.keySet().stream().map(id -> Map.entry(id, DISPLAY_NAMES.get(id))).toList();
    }

    private static ClientRegistration build(String id, OAuthProperties.Client c) {
        return switch (id) {
            case "google" -> CommonOAuth2Provider.GOOGLE.getBuilder(id)
                    .clientId(c.clientId()).clientSecret(c.clientSecret())
                    .redirectUri(REDIRECT)
                    .scope("openid", "profile", "email")
                    .build();
            case "github" -> CommonOAuth2Provider.GITHUB.getBuilder(id)
                    .clientId(c.clientId()).clientSecret(c.clientSecret())
                    .redirectUri(REDIRECT)
                    .scope("read:user", "user:email")
                    .build();
            case "linkedin" -> ClientRegistration.withRegistrationId(id)
                    .clientName("LinkedIn")
                    .clientId(c.clientId()).clientSecret(c.clientSecret())
                    // LinkedIn exige las credenciales en el cuerpo de la petición del token.
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri(REDIRECT)
                    .scope("openid", "profile", "email")
                    .authorizationUri("https://www.linkedin.com/oauth/v2/authorization")
                    .tokenUri("https://www.linkedin.com/oauth/v2/accessToken")
                    .jwkSetUri("https://www.linkedin.com/oauth/openid/jwks")
                    .userInfoUri("https://api.linkedin.com/v2/userinfo")
                    .issuerUri("https://www.linkedin.com/oauth")
                    .userNameAttributeName(IdTokenClaimNames.SUB)
                    .build();
            default -> throw new IllegalArgumentException("Proveedor no soportado: " + id);
        };
    }
}
