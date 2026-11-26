package com.plataformaempleo.realtime.call.infrastructure;

import com.plataformaempleo.realtime.call.domain.model.CallGrant;
import com.plataformaempleo.realtime.call.domain.port.out.CallGrantVerifier;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Valida el token de sala de la API: HS256 con el mismo secreto, emisor {@value #ISSUER} y audiencia {@value #AUDIENCE}.
 * Un token de acceso normal (emisor worksy-api) no sirve aquí, y este token no sirve para conectarse ni para la API.
 */
class JwtCallGrantVerifier implements CallGrantVerifier {
    static final String ISSUER = "worksy-call";
    static final String AUDIENCE = "worksy-realtime";

    private final NimbusJwtDecoder decoder;

    JwtCallGrantVerifier(String secret) {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(AUDIENCE)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Audiencia incorrecta", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(ISSUER),
                new JwtTimestampValidator(java.time.Duration.ZERO), audience));
    }

    @Override
    public CallGrant verify(String roomToken) {
        if (roomToken == null || roomToken.isBlank()) throw new IllegalArgumentException("Falta el token de sala");
        try {
            Jwt jwt = decoder.decode(roomToken);
            List<String> peers = jwt.getClaimAsStringList("peers");
            return new CallGrant(UUID.fromString(jwt.getSubject()), UUID.fromString(jwt.getClaimAsString("sid")),
                    UUID.fromString(jwt.getClaimAsString("iid")), jwt.getClaimAsString("role"),
                    peers == null ? List.of() : peers.stream().map(UUID::fromString).toList(), jwt.getExpiresAt());
        } catch (JwtException | NullPointerException e) {
            throw new IllegalArgumentException("Token de sala inválido", e);
        }
    }
}
