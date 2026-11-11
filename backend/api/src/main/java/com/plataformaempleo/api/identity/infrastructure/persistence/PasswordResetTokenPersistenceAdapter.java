package com.plataformaempleo.api.identity.infrastructure.persistence;

import com.plataformaempleo.api.identity.domain.model.PasswordResetToken;
import com.plataformaempleo.api.identity.domain.port.out.PasswordResetTokenRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador JPA del puerto {@link PasswordResetTokenRepository}. */
@Component
class PasswordResetTokenPersistenceAdapter implements PasswordResetTokenRepository {

    private final SpringDataPasswordResetTokenRepository jpa;

    PasswordResetTokenPersistenceAdapter(SpringDataPasswordResetTokenRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public PasswordResetToken save(PasswordResetToken t) {
        return toDomain(jpa.saveAndFlush(new PasswordResetTokenJpaEntity(t.id(), t.userId(), t.tokenHash(),
                t.createdAt(), t.expiresAt(), t.usedAt())));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<PasswordResetToken> findByHashForUpdate(String tokenHash) {
        return jpa.findByHashForUpdate(tokenHash).map(PasswordResetTokenPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public int invalidatePendingForUser(UUID userId, Instant now) {
        return jpa.invalidatePendingForUser(userId, now);
    }

    private static PasswordResetToken toDomain(PasswordResetTokenJpaEntity e) {
        return new PasswordResetToken(e.getId(), e.getUserId(), e.getTokenHash(), e.getCreatedAt(), e.getExpiresAt(),
                e.getUsedAt());
    }
}
