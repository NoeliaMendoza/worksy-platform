package com.plataformaempleo.api.identity.infrastructure.persistence;

import com.plataformaempleo.api.identity.domain.model.RefreshToken;
import com.plataformaempleo.api.identity.domain.port.out.RefreshTokenRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador JPA del puerto {@link RefreshTokenRepository}. Participa en la transacción del caso de uso. */
@Component
class RefreshTokenPersistenceAdapter implements RefreshTokenRepository {

    private final SpringDataRefreshTokenRepository jpa;

    RefreshTokenPersistenceAdapter(SpringDataRefreshTokenRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public RefreshToken save(RefreshToken token) {
        return toDomain(jpa.saveAndFlush(toEntity(token)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<RefreshToken> findByHashForUpdate(String tokenHash) {
        return jpa.findByHashForUpdate(tokenHash).map(RefreshTokenPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public int revokeFamily(UUID familyId, Instant now) {
        return jpa.revokeFamily(familyId, now);
    }

    @Override
    @Transactional
    public int revokeAllForUser(UUID userId, Instant now) {
        return jpa.revokeAllForUser(userId, now);
    }

    private static RefreshTokenJpaEntity toEntity(RefreshToken t) {
        return new RefreshTokenJpaEntity(t.id(), t.userId(), t.familyId(), t.tokenHash(), t.issuedAt(),
                t.expiresAt(), t.revokedAt(), t.replacedBy());
    }

    private static RefreshToken toDomain(RefreshTokenJpaEntity e) {
        return new RefreshToken(e.getId(), e.getUserId(), e.getFamilyId(), e.getTokenHash(), e.getIssuedAt(),
                e.getExpiresAt(), e.getRevokedAt(), e.getReplacedBy());
    }
}
