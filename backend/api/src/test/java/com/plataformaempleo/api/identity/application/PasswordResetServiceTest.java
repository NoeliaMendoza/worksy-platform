package com.plataformaempleo.api.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plataformaempleo.api.identity.domain.model.PasswordResetToken;
import com.plataformaempleo.api.identity.domain.model.RefreshToken;
import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.identity.domain.model.User;
import com.plataformaempleo.api.identity.domain.port.out.PasswordHasher;
import com.plataformaempleo.api.identity.domain.port.out.PasswordResetTokenRepository;
import com.plataformaempleo.api.identity.domain.port.out.RefreshTokenRepository;
import com.plataformaempleo.api.identity.domain.port.out.UserRepository;
import com.plataformaempleo.common.domain.DomainException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Recuperación de contraseña con dobles en memoria. Datos sintéticos. */
class PasswordResetServiceTest {

    private final Map<UUID, User> userStore = new HashMap<>();
    private final Map<UUID, PasswordResetToken> resetStore = new HashMap<>();
    private final List<String> revokedSessionsFor = new ArrayList<>();
    private final List<String[]> sent = new ArrayList<>();
    private Instant now = Instant.parse("2026-11-09T12:00:00Z");
    private PasswordResetService service;
    private User ana;

    @BeforeEach
    void setUp() {
        Clock clock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now; }
        };
        service = new PasswordResetService(new Users(), new ResetTokens(), new Sessions(), new Hasher(),
                (email, link, minutes) -> sent.add(new String[] {email, link, String.valueOf(minutes)}),
                clock, Duration.ofMinutes(30), "http://localhost:5173/restablecer");
        ana = User.register("ana@ejemplo.test", "hash:vieja-frase-larga", Role.CANDIDATE, null, clock);
        userStore.put(ana.id(), ana);
    }

    @Test
    void enviaUnEnlaceDeUnSoloUsoALaCuentaQueExiste() {
        service.requestReset("ANA@ejemplo.test");

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0)[0]).isEqualTo("ana@ejemplo.test");
        assertThat(sent.get(0)[1]).startsWith("http://localhost:5173/restablecer?token=");
        assertThat(sent.get(0)[2]).isEqualTo("30");
        assertThat(resetStore.values()).singleElement()
                .satisfies(t -> assertThat(t.tokenHash()).isNotEqualTo(secretOf(sent.get(0)[1])));
    }

    @Test
    void noHaceNadaVisibleSiLaCuentaNoExisteOElCorreoEsInvalido() {
        service.requestReset("nadie@ejemplo.test");
        service.requestReset("no-es-correo");
        assertThat(sent).isEmpty();
        assertThat(resetStore).isEmpty();
    }

    @Test
    void cambiaLaContrasenaCierraLasSesionesYElEnlaceNoSirveDosVeces() {
        service.requestReset("ana@ejemplo.test");
        String secret = secretOf(sent.get(0)[1]);

        service.resetPassword(secret, "nueva-frase-larga");

        assertThat(userStore.get(ana.id()).passwordHash()).isEqualTo("hash:nueva-frase-larga");
        assertThat(revokedSessionsFor).containsExactly(ana.id().toString());
        assertThat(codeOf(() -> service.resetPassword(secret, "otra-frase-larga"))).isEqualTo("password.reset.invalid");
    }

    @Test
    void pedirUnEnlaceNuevoInvalidaElAnterior() {
        service.requestReset("ana@ejemplo.test");
        service.requestReset("ana@ejemplo.test");
        String first = secretOf(sent.get(0)[1]);
        String second = secretOf(sent.get(1)[1]);

        assertThat(codeOf(() -> service.resetPassword(first, "nueva-frase-larga"))).isEqualTo("password.reset.invalid");
        service.resetPassword(second, "nueva-frase-larga");
    }

    @Test
    void elEnlaceCaducaYLaContrasenaNuevaRespetaLaPolitica() {
        service.requestReset("ana@ejemplo.test");
        String secret = secretOf(sent.get(0)[1]);

        assertThat(codeOf(() -> service.resetPassword(secret, "corta"))).isEqualTo("user.password.invalid");
        now = now.plus(Duration.ofMinutes(31));
        assertThat(codeOf(() -> service.resetPassword(secret, "nueva-frase-larga"))).isEqualTo("password.reset.invalid");
        assertThat(codeOf(() -> service.resetPassword("inventado", "nueva-frase-larga"))).isEqualTo("password.reset.invalid");
    }

    private static String secretOf(String link) {
        return URLDecoder.decode(link.substring(link.indexOf("token=") + 6), StandardCharsets.UTF_8);
    }

    private static String codeOf(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(DomainException.class);
        try {
            action.run();
            return null;
        } catch (DomainException e) {
            return e.code();
        }
    }

    // ---- Dobles en memoria ----

    private final class Users implements UserRepository {
        @Override public User save(User u) { userStore.put(u.id(), u); return u; }
        @Override public Optional<User> findById(UUID id) { return Optional.ofNullable(userStore.get(id)); }
        @Override public Optional<User> findActiveByEmail(String email) {
            String n = User.normalizeEmail(email);
            return userStore.values().stream().filter(u -> u.email().equals(n)).findFirst();
        }
    }

    private final class ResetTokens implements PasswordResetTokenRepository {
        @Override public PasswordResetToken save(PasswordResetToken t) { resetStore.put(t.id(), t); return t; }
        @Override public Optional<PasswordResetToken> findByHashForUpdate(String hash) {
            return resetStore.values().stream().filter(t -> t.tokenHash().equals(hash)).findFirst();
        }
        @Override public int invalidatePendingForUser(UUID userId, Instant at) {
            var pending = resetStore.values().stream().filter(t -> t.userId().equals(userId) && t.usedAt() == null).toList();
            pending.forEach(t -> resetStore.put(t.id(), t.markUsed(at)));
            return pending.size();
        }
    }

    private final class Sessions implements RefreshTokenRepository {
        @Override public RefreshToken save(RefreshToken t) { return t; }
        @Override public Optional<RefreshToken> findByHashForUpdate(String h) { return Optional.empty(); }
        @Override public int revokeFamily(UUID f, Instant at) { return 0; }
        @Override public int revokeAllForUser(UUID userId, Instant at) { revokedSessionsFor.add(userId.toString()); return 1; }
    }

    private static final class Hasher implements PasswordHasher {
        @Override public String hash(String raw) { return "hash:" + raw; }
        @Override public boolean matches(String raw, String hash) { return hash.equals("hash:" + raw); }
    }
}
