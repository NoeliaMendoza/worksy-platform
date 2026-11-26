package com.plataformaempleo.api.interview.infrastructure.security;

import com.plataformaempleo.api.interview.domain.model.CallRoom;
import com.plataformaempleo.api.interview.domain.port.out.CallTokenIssuer;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Token de sala: JWT HS256 con emisor propio ({@value #ISSUER}) y audiencia {@value #AUDIENCE}. Por el emisor distinto,
 * la API y la conexión del WebSocket lo rechazan como token de acceso; solo sirve para reenviar señales a las personas
 * de {@code peers} hasta que se cierra la sala.
 */
@Component
class JwtCallTokenIssuer implements CallTokenIssuer {
    static final String ISSUER = "worksy-call";
    static final String AUDIENCE = "worksy-realtime";

    private final JwtEncoder encoder;
    private final Clock clock;

    JwtCallTokenIssuer(JwtEncoder encoder, Clock clock) {
        this.encoder = encoder;
        this.clock = clock;
    }

    @Override
    public String issue(CallRoom.Grant grant) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(grant.userId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(clock.instant())
                .expiresAt(grant.expiresAt())
                .claim("sid", grant.sessionId().toString())
                .claim("iid", grant.interviewId().toString())
                .claim("role", grant.role().name())
                .claim("peers", grant.peers().stream().map(UUID::toString).toList())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
