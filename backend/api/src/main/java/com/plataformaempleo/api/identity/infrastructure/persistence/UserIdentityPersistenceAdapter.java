package com.plataformaempleo.api.identity.infrastructure.persistence;

import com.plataformaempleo.api.identity.domain.model.ExternalIdentity;
import com.plataformaempleo.api.identity.domain.port.out.ExternalIdentityRepository;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador JPA del puerto {@link ExternalIdentityRepository}. */
@Component
class UserIdentityPersistenceAdapter implements ExternalIdentityRepository {

    private final SpringDataUserIdentityRepository jpa;

    UserIdentityPersistenceAdapter(SpringDataUserIdentityRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ExternalIdentity> findByProviderAndSubject(String provider, String subject) {
        return jpa.findByProviderAndSubject(provider.toLowerCase(Locale.ROOT), subject)
                .map(e -> new ExternalIdentity(e.getId(), e.getUserId(), e.getProvider(), e.getSubject(), e.getEmail(),
                        e.getCreatedAt()));
    }

    @Override
    @Transactional
    public ExternalIdentity save(ExternalIdentity i) {
        jpa.saveAndFlush(new UserIdentityJpaEntity(i.id(), i.userId(), i.provider(), i.subject(), i.email(),
                i.createdAt()));
        return i;
    }
}
