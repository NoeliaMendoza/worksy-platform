package com.plataformaempleo.api.identity.infrastructure.security;

import com.plataformaempleo.api.identity.domain.model.AccessToken;
import com.plataformaempleo.api.identity.domain.model.User;
import com.plataformaempleo.api.identity.domain.port.out.AccessTokenIssuer;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Emite JWT HS256. Claims: {@code sub} (id), {@code role} y, si aplica, {@code company_id}.
 * No incluye el correo ni otros datos personales.
 */
@Component
class JwtAccessTokenIssuer implements AccessTokenIssuer {

    static final String ROLE_CLAIM = "role";
    static final String COMPANY_CLAIM = "company_id";

    private final JwtEncoder encoder;
    private final AuthProperties props;
    private final Clock clock;

    JwtAccessTokenIssuer(JwtEncoder encoder, AuthProperties props, Clock clock) {
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
    }

    @Override
    public AccessToken issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(props.accessTokenTtl());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .subject(user.id().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(ROLE_CLAIM, user.role().name());
        if (user.companyId() != null) {
            claims.claim(COMPANY_CLAIM, user.companyId().toString());
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new AccessToken(token, expiresAt);
    }
}
