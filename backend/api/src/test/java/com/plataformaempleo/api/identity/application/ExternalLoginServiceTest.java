package com.plataformaempleo.api.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plataformaempleo.api.identity.application.ExternalLoginService.ExternalProfile;
import com.plataformaempleo.api.identity.domain.model.AccessToken;
import com.plataformaempleo.api.identity.domain.model.ExternalIdentity;
import com.plataformaempleo.api.identity.domain.model.RefreshToken;
import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.identity.domain.model.User;
import com.plataformaempleo.api.identity.domain.port.out.ExternalIdentityRepository;
import com.plataformaempleo.api.identity.domain.port.out.PasswordHasher;
import com.plataformaempleo.api.identity.domain.port.out.RefreshTokenRepository;
import com.plataformaempleo.api.identity.domain.port.out.UserRepository;
import com.plataformaempleo.common.domain.DomainException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Inicio de sesión con cuentas externas, con dobles en memoria. Datos sintéticos. */
class ExternalLoginServiceTest {

    private final Map<UUID, User> userStore = new HashMap<>();
    private final List<ExternalIdentity> identityStore = new ArrayList<>();
    private ExternalLoginService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), java.time.ZoneOffset.UTC);
        UserRepository users = new UserRepository() {
            @Override public User save(User u) { userStore.put(u.id(), u); return u; }
            @Override public Optional<User> findById(UUID id) { return Optional.ofNullable(userStore.get(id)); }
            @Override public Optional<User> findActiveByEmail(String email) {
                String n = User.normalizeEmail(email);
                return userStore.values().stream().filter(u -> u.email().equals(n)).findFirst();
            }
        };
        ExternalIdentityRepository identities = new ExternalIdentityRepository() {
            @Override public Optional<ExternalIdentity> findByProviderAndSubject(String provider, String subject) {
                return identityStore.stream()
                        .filter(i -> i.provider().equals(provider) && i.subject().equals(subject)).findFirst();
            }
            @Override public ExternalIdentity save(ExternalIdentity i) { identityStore.add(i); return i; }
        };
        RefreshTokenRepository sessions = new RefreshTokenRepository() {
            @Override public RefreshToken save(RefreshToken t) { return t; }
            @Override public Optional<RefreshToken> findByHashForUpdate(String h) { return Optional.empty(); }
            @Override public int revokeFamily(UUID f, Instant now) { return 0; }
            @Override public int revokeAllForUser(UUID u, Instant now) { return 0; }
        };
        PasswordHasher hasher = new PasswordHasher() {
            @Override public String hash(String raw) { return "hash:" + raw; }
            @Override public boolean matches(String raw, String hash) { return hash.equals("hash:" + raw); }
        };
        AuthService auth = new AuthService(users, sessions, hasher,
                u -> new AccessToken("jwt-" + u.id(), clock.instant().plusSeconds(900)), clock, Duration.ofDays(7));
        service = new ExternalLoginService(users, identities, hasher, auth, clock);
    }

    @Test
    void creaUnaCuentaDeCandidatoConCorreoVerificadoYLaVincula() {
        var session = service.login(new ExternalProfile("google", "g-123", "Nuevo@Ejemplo.test", true));

        assertThat(session.user().role()).isEqualTo(Role.CANDIDATE);
        assertThat(session.user().email()).isEqualTo("nuevo@ejemplo.test");
        assertThat(identityStore).singleElement().satisfies(i -> {
            assertThat(i.provider()).isEqualTo("google");
            assertThat(i.subject()).isEqualTo("g-123");
        });
    }

    @Test
    void vinculaUnaCuentaExistenteConElMismoCorreoVerificado() {
        User existing = User.register("ana@ejemplo.test", "hash:x", Role.CANDIDATE, null, Clock.systemUTC());
        userStore.put(existing.id(), existing);

        var session = service.login(new ExternalProfile("linkedin", "li-9", "ana@ejemplo.test", true));

        assertThat(session.user().id()).isEqualTo(existing.id());
        assertThat(userStore).hasSize(1);
    }

    @Test
    void laSegundaVezEntraPorElVinculoAunqueElCorreoCambie() {
        var first = service.login(new ExternalProfile("github", "42", "dev@ejemplo.test", true));
        var second = service.login(new ExternalProfile("github", "42", "otro@ejemplo.test", false));

        assertThat(second.user().id()).isEqualTo(first.user().id());
        assertThat(identityStore).hasSize(1);
    }

    @Test
    void rechazaUnCorreoNoVerificadoSinVinculoPrevio() {
        User existing = User.register("ana@ejemplo.test", "hash:x", Role.CANDIDATE, null, Clock.systemUTC());
        userStore.put(existing.id(), existing);

        assertThatThrownBy(() -> service.login(new ExternalProfile("github", "7", "ana@ejemplo.test", false)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo("auth.external.email-unverified");
        assertThatThrownBy(() -> service.login(new ExternalProfile("github", "8", null, true)))
                .isInstanceOf(DomainException.class);
        assertThat(identityStore).isEmpty();
    }
}
