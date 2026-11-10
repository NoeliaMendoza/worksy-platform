package com.plataformaempleo.api.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plataformaempleo.api.identity.domain.model.AccessToken;
import com.plataformaempleo.api.identity.domain.model.AuthenticationFailedException;
import com.plataformaempleo.api.identity.domain.model.RefreshToken;
import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.identity.domain.model.User;
import com.plataformaempleo.api.identity.domain.port.out.PasswordHasher;
import com.plataformaempleo.api.identity.domain.port.out.RefreshTokenRepository;
import com.plataformaempleo.api.identity.domain.port.out.UserRepository;
import com.plataformaempleo.common.domain.DomainException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Casos de uso con dobles en memoria: sin Spring, sin base de datos. Datos sintéticos. */
class AuthServiceTest {

    private static final String PASSWORD = "frase-sintetica-larga";

    private final Map<UUID, User> userStore = new HashMap<>();
    private final Map<UUID, RefreshToken> tokenStore = new HashMap<>();
    private MutableClock clock;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-09T12:00:00Z"));
        auth = new AuthService(new InMemoryUsers(), new InMemoryTokens(), new FakeHasher(),
                user -> new AccessToken("jwt-" + user.id(), clock.instant().plusSeconds(900)),
                clock, Duration.ofDays(7));
    }

    @Test
    void registraCandidatosConLaContrasenaHasheada() {
        User user = auth.registerCandidate("Ana@Ejemplo.test", PASSWORD);

        assertThat(user.role()).isEqualTo(Role.CANDIDATE);
        assertThat(user.email()).isEqualTo("ana@ejemplo.test");
        assertThat(user.passwordHash()).isEqualTo("hash:" + PASSWORD);
    }

    @Test
    void rechazaCorreoDuplicadoYContrasenaCorta() {
        auth.registerCandidate("ana@ejemplo.test", PASSWORD);

        assertThatThrownBy(() -> auth.registerCandidate("ANA@ejemplo.test", PASSWORD))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo("user.email.taken");
        assertThatThrownBy(() -> auth.registerCandidate("otra@ejemplo.test", "corta"))
                .extracting(e -> ((DomainException) e).code()).isEqualTo("user.password.invalid");
    }

    @Test
    void elInicioDeSesionFallaIgualConCorreoInexistenteOContrasenaErronea() {
        auth.registerCandidate("ana@ejemplo.test", PASSWORD);

        var wrongPassword = catchCode(() -> auth.login("ana@ejemplo.test", "otra-frase-larga"));
        var unknownEmail = catchCode(() -> auth.login("nadie@ejemplo.test", PASSWORD));
        var malformedEmail = catchCode(() -> auth.login("no-es-correo", PASSWORD));

        assertThat(wrongPassword).isEqualTo("auth.credentials.invalid");
        assertThat(unknownEmail).isEqualTo(wrongPassword);
        assertThat(malformedEmail).isEqualTo(wrongPassword);
    }

    @Test
    void elRefrescoRotaElToken() {
        auth.registerCandidate("ana@ejemplo.test", PASSWORD);
        var first = auth.login("ana@ejemplo.test", PASSWORD);

        clock.advance(Duration.ofMinutes(20));
        var second = auth.refresh(first.refreshSecret());

        assertThat(second.refreshSecret()).isNotEqualTo(first.refreshSecret());
        RefreshToken old = tokenStore.values().stream()
                .filter(t -> t.tokenHash().equals(RefreshToken.hash(first.refreshSecret()))).findFirst().orElseThrow();
        assertThat(old.wasRotated()).isTrue();
        assertThat(old.replacedBy()).isEqualTo(second.refreshTokenId());
    }

    @Test
    void reutilizarUnTokenRotadoRevocaTodaLaSesion() {
        auth.registerCandidate("ana@ejemplo.test", PASSWORD);
        var first = auth.login("ana@ejemplo.test", PASSWORD);
        var second = auth.refresh(first.refreshSecret());

        assertThat(catchCode(() -> auth.refresh(first.refreshSecret()))).isEqualTo("auth.refresh.reused");
        // El token legítimo más reciente también queda revocado (sin rotar: responde como inválido).
        assertThat(catchCode(() -> auth.refresh(second.refreshSecret()))).isEqualTo("auth.refresh.invalid");
    }

    @Test
    void elRefrescoCaducadoODesconocidoFalla() {
        auth.registerCandidate("ana@ejemplo.test", PASSWORD);
        var session = auth.login("ana@ejemplo.test", PASSWORD);

        assertThat(catchCode(() -> auth.refresh("inventado"))).isEqualTo("auth.refresh.invalid");
        assertThat(catchCode(() -> auth.refresh(null))).isEqualTo("auth.refresh.invalid");
        clock.advance(Duration.ofDays(8));
        assertThat(catchCode(() -> auth.refresh(session.refreshSecret()))).isEqualTo("auth.refresh.invalid");
    }

    @Test
    void cerrarSesionRevocaElRefrescoYEsIdempotente() {
        auth.registerCandidate("ana@ejemplo.test", PASSWORD);
        var session = auth.login("ana@ejemplo.test", PASSWORD);

        auth.logout(session.refreshSecret());
        auth.logout(session.refreshSecret());
        auth.logout(null);

        assertThat(catchCode(() -> auth.refresh(session.refreshSecret()))).isEqualTo("auth.refresh.invalid");
    }

    private static String catchCode(Runnable action) {
        try {
            action.run();
        } catch (AuthenticationFailedException e) {
            return e.code();
        }
        throw new AssertionError("Se esperaba AuthenticationFailedException");
    }

    // ---- Dobles en memoria de los puertos ----

    private final class InMemoryUsers implements UserRepository {
        @Override
        public User save(User user) {
            userStore.put(user.id(), user);
            return user;
        }

        @Override
        public Optional<User> findById(UUID id) {
            return Optional.ofNullable(userStore.get(id));
        }

        @Override
        public Optional<User> findActiveByEmail(String email) {
            String normalized = User.normalizeEmail(email);
            return userStore.values().stream().filter(u -> !u.isDeleted() && u.email().equals(normalized)).findFirst();
        }
    }

    private final class InMemoryTokens implements RefreshTokenRepository {
        @Override
        public RefreshToken save(RefreshToken token) {
            tokenStore.put(token.id(), token);
            return token;
        }

        @Override
        public Optional<RefreshToken> findByHashForUpdate(String tokenHash) {
            return tokenStore.values().stream().filter(t -> t.tokenHash().equals(tokenHash)).findFirst();
        }

        @Override
        public int revokeFamily(UUID familyId, Instant now) {
            int count = 0;
            for (var t : tokenStore.values().stream().filter(t -> t.familyId().equals(familyId) && t.revokedAt() == null).toList()) {
                tokenStore.put(t.id(), new RefreshToken(t.id(), t.userId(), t.familyId(), t.tokenHash(), t.issuedAt(),
                        t.expiresAt(), now, t.replacedBy()));
                count++;
            }
            return count;
        }

        @Override
        public int revokeAllForUser(UUID userId, Instant now) {
            int count = 0;
            for (var t : tokenStore.values().stream().filter(t -> t.userId().equals(userId) && t.revokedAt() == null).toList()) {
                count += revokeFamily(t.familyId(), now);
            }
            return count;
        }
    }

    private static final class FakeHasher implements PasswordHasher {
        @Override
        public String hash(String raw) {
            return "hash:" + raw;
        }

        @Override
        public boolean matches(String raw, String hash) {
            return hash.equals("hash:" + raw);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
