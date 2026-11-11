package com.plataformaempleo.api.identity.application;

import com.plataformaempleo.api.identity.domain.model.AccessToken;
import com.plataformaempleo.api.identity.domain.model.AuthenticationFailedException;
import com.plataformaempleo.api.identity.domain.model.PasswordPolicy;
import com.plataformaempleo.api.identity.domain.model.RefreshToken;
import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.identity.domain.model.User;
import com.plataformaempleo.api.identity.domain.port.out.AccessTokenIssuer;
import com.plataformaempleo.api.identity.domain.port.out.PasswordHasher;
import com.plataformaempleo.api.identity.domain.port.out.RefreshTokenRepository;
import com.plataformaempleo.api.identity.domain.port.out.UserRepository;
import com.plataformaempleo.common.domain.DomainException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de autenticación: registro de candidatos, inicio de sesión,
 * rotación del token de refresco, cierre de sesión y usuario actual.
 * Solo conoce puertos; la configuración (duraciones) llega por constructor.
 */
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordHasher hasher;
    private final AccessTokenIssuer accessTokens;
    private final Clock clock;
    private final Duration refreshTtl;
    /** Hash de relleno: se verifica aunque el correo no exista, para no revelar su existencia por tiempo de respuesta. */
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordHasher hasher,
                       AccessTokenIssuer accessTokens, Clock clock, Duration refreshTtl) {
        this.users = Objects.requireNonNull(users);
        this.refreshTokens = Objects.requireNonNull(refreshTokens);
        this.hasher = Objects.requireNonNull(hasher);
        this.accessTokens = Objects.requireNonNull(accessTokens);
        this.clock = Objects.requireNonNull(clock);
        this.refreshTtl = Objects.requireNonNull(refreshTtl);
        this.dummyHash = hasher.hash(UUID.randomUUID().toString());
    }

    /** Registro público: solo candidatos. Las cuentas de empresa se crean desde el módulo de empresas. */
    @Transactional
    public User registerCandidate(String email, String password) {
        PasswordPolicy.validate(password);
        String normalized = User.normalizeEmail(email);
        if (users.findActiveByEmail(normalized).isPresent()) {
            throw new DomainException("user.email.taken", "Ya existe una cuenta con ese correo");
        }
        return users.save(User.register(normalized, hasher.hash(password), Role.CANDIDATE, null, clock));
    }

    @Transactional
    public AuthSession login(String email, String password) {
        // Se valida antes de consultar: si el repositorio lanzara la excepción dentro de su transacción,
        // la dejaría marcada para revertirse y el error sería un 500 en vez de un 401.
        User user = validEmail(email) ? users.findActiveByEmail(email).orElse(null) : null;
        String hash = user != null ? user.passwordHash() : dummyHash;
        boolean matches = password != null && hasher.matches(password, hash);
        if (user == null || !matches) {
            throw AuthenticationFailedException.badCredentials();
        }
        return openSession(user, UUID.randomUUID());
    }

    /**
     * Rota el token: el usado queda revocado y apunta a su sucesor.
     * Si llega un token ya rotado, alguien lo copió: se revoca toda la familia.
     */
    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public AuthSession refresh(String refreshSecret) {
        if (refreshSecret == null || refreshSecret.isBlank()) {
            throw AuthenticationFailedException.invalidRefresh();
        }
        Instant now = clock.instant();
        RefreshToken current = refreshTokens.findByHashForUpdate(RefreshToken.hash(refreshSecret))
                .orElseThrow(AuthenticationFailedException::invalidRefresh);

        if (current.wasRotated()) {
            refreshTokens.revokeFamily(current.familyId(), now);
            throw new AuthenticationFailedException("auth.refresh.reused");
        }
        if (!current.isActive(now)) {
            throw AuthenticationFailedException.invalidRefresh();
        }
        User user = users.findById(current.userId())
                .filter(u -> !u.isDeleted())
                .orElseThrow(AuthenticationFailedException::invalidRefresh);

        AuthSession session = openSession(user, current.familyId());
        refreshTokens.save(current.rotatedTo(session.refreshTokenId(), now));
        return session;
    }

    /** Cierra la sesión revocando la familia del token. Es idempotente: un token desconocido no es error. */
    @Transactional
    public void logout(String refreshSecret) {
        if (refreshSecret == null || refreshSecret.isBlank()) {
            return;
        }
        refreshTokens.findByHashForUpdate(RefreshToken.hash(refreshSecret))
                .ifPresent(token -> refreshTokens.revokeFamily(token.familyId(), clock.instant()));
    }

    /** Abre una sesión nueva para un usuario ya verificado por otro medio (p. ej., una cuenta externa). */
    @Transactional
    public AuthSession startSession(User user) {
        return openSession(user, UUID.randomUUID());
    }

    @Transactional(readOnly = true)
    public User currentUser(UUID userId) {
        return users.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(AuthenticationFailedException::invalidRefresh);
    }

    /** Formato de correo válido según el dominio, sin lanzar excepciones. */
    public static boolean validEmail(String email) {
        try {
            User.normalizeEmail(email);
            return true;
        } catch (DomainException invalid) {
            return false;
        }
    }

    private AuthSession openSession(User user, UUID familyId) {
        RefreshToken.Issued refresh = RefreshToken.issue(user.id(), familyId, clock.instant(), refreshTtl);
        refreshTokens.save(refresh.token());
        AccessToken access = accessTokens.issue(user);
        return new AuthSession(user, access, refresh.secret(), refresh.token().expiresAt(), refresh.token().id());
    }

    /** Resultado de iniciar o renovar sesión. {@code refreshSecret} solo viaja en la cookie. */
    public record AuthSession(User user, AccessToken accessToken, String refreshSecret,
                              Instant refreshExpiresAt, UUID refreshTokenId) {
    }
}
