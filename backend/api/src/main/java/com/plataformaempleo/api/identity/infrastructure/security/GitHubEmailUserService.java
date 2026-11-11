package com.plataformaempleo.api.identity.infrastructure.security;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.client.RestClient;

/**
 * GitHub no es OpenID Connect y su perfil puede no traer el correo (si es privado).
 * Este servicio pide {@code /user/emails} y añade el correo principal y si está verificado,
 * con los mismos nombres que usa OIDC: {@code email} y {@code email_verified}.
 */
class GitHubEmailUserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final String EMAILS_URL = "https://api.github.com/user/emails";

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final RestClient http;

    GitHubEmailUserService(RestClient.Builder builder) {
        this.http = builder.build();
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User user = delegate.loadUser(request);
        if (!"github".equals(request.getClientRegistration().getRegistrationId())) {
            return user;
        }
        List<Map<String, Object>> emails = http.get().uri(EMAILS_URL)
                .header("Authorization", "Bearer " + request.getAccessToken().getTokenValue())
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .body(new ParameterizedTypeReference<>() { });

        Map<String, Object> attributes = new HashMap<>(user.getAttributes());
        attributes.remove("email");
        attributes.put("email_verified", false);
        if (emails != null) {
            emails.stream()
                    .filter(e -> Boolean.TRUE.equals(e.get("primary")))
                    .findFirst()
                    .ifPresent(primary -> {
                        attributes.put("email", primary.get("email"));
                        attributes.put("email_verified", Boolean.TRUE.equals(primary.get("verified")));
                    });
        }
        return new DefaultOAuth2User(user.getAuthorities(), attributes, "id");
    }
}
