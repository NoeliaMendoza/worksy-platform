package com.plataformaempleo.api.identity.infrastructure.persistence;

import com.plataformaempleo.api.identity.domain.model.User;
import com.plataformaempleo.api.identity.domain.port.out.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador de salida que implementa el puerto {@link UserRepository} con JPA. */
@Component
class UserPersistenceAdapter implements UserRepository {

    private final SpringDataUserRepository jpa;

    UserPersistenceAdapter(SpringDataUserRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public User save(User user) {
        return toDomain(jpa.saveAndFlush(toEntity(user)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(UUID id) {
        return jpa.findById(id).map(UserPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findActiveByEmail(String email) {
        return jpa.findByEmailAndDeletedAtIsNull(User.normalizeEmail(email))
                .map(UserPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findActiveByCompany(UUID companyId) {
        return jpa.findByCompanyIdAndDeletedAtIsNullOrderByCreatedAtAsc(companyId).stream()
                .map(UserPersistenceAdapter::toDomain)
                .toList();
    }

    private static UserJpaEntity toEntity(User u) {
        return new UserJpaEntity(u.id(), u.email(), u.passwordHash(), u.role(), u.companyId(),
                u.createdAt(), u.deletedAt());
    }

    private static User toDomain(UserJpaEntity e) {
        return new User(e.getId(), e.getEmail(), e.getPasswordHash(), e.getRole(), e.getCompanyId(),
                e.getCreatedAt(), e.getDeletedAt());
    }
}
