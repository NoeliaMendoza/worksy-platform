package com.plataformaempleo.api.identity.application;

import com.plataformaempleo.api.identity.domain.model.PasswordPolicy;
import com.plataformaempleo.api.identity.domain.model.PasswordResetToken;
import com.plataformaempleo.api.identity.domain.model.SecureTokens;
import com.plataformaempleo.api.identity.domain.model.User;
import com.plataformaempleo.api.identity.domain.port.out.PasswordHasher;
import com.plataformaempleo.api.identity.domain.port.out.PasswordResetMailer;
import com.plataformaempleo.api.identity.domain.port.out.PasswordResetTokenRepository;
import com.plataformaempleo.api.identity.domain.port.out.RefreshTokenRepository;
import com.plataformaempleo.api.identity.domain.port.out.UserRepository;
import com.plataformaempleo.common.domain.DomainException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recuperar contraseña: pedir un enlace por correo y usarlo para elegir una contraseña nueva.
 * Pedir el enlace nunca revela si la cuenta existe.
 */
public class PasswordResetService {

    private final UserRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordHasher hasher;
    private final PasswordResetMailer mailer;
    private final Clock clock;
    private final Duration ttl;
    private final String resetPageUrl;

    /**
     * @param resetPageUrl página del frontend que recibe el enlace, p. ej. {@code http://localhost:5173/restablecer}.
     */
    public PasswordResetService(UserRepository users, PasswordResetTokenRepository resetTokens,
                                RefreshTokenRepository refreshTokens, PasswordHasher hasher,
                                PasswordResetMailer mailer, Clock clock, Duration ttl, String resetPageUrl) {
        this.users = Objects.requireNonNull(users);
        this.resetTokens = Objects.requireNonNull(resetTokens);
        this.refreshTokens = Objects.requireNonNull(refreshTokens);
        this.hasher = Objects.requireNonNull(hasher);
        this.mailer = Objects.requireNonNull(mailer);
        this.clock = Objects.requireNonNull(clock);
        this.ttl = Objects.requireNonNull(ttl);
        this.resetPageUrl = Objects.requireNonNull(resetPageUrl);
    }

    /** Si la cuenta existe, invalida los enlaces anteriores y envía uno nuevo. Si no, no hace nada visible. */
    @Transactional
    public void requestReset(String email) {
        if (!AuthService.validEmail(email)) {
            return;
        }
        User user = users.findActiveByEmail(email).orElse(null);
        if (user == null) {
            return;
        }
        Instant now = clock.instant();
        resetTokens.invalidatePendingForUser(user.id(), now);
        PasswordResetToken.Issued issued = PasswordResetToken.issue(user.id(), now, ttl);
        resetTokens.save(issued.token());
        String link = resetPageUrl + "?token=" + URLEncoder.encode(issued.secret(), StandardCharsets.UTF_8);
        mailer.sendResetLink(user.email(), link, ttl.toMinutes());
    }

    /** Cambia la contraseña con un enlace válido y cierra todas las sesiones abiertas del usuario. */
    @Transactional
    public void resetPassword(String secret, String newPassword) {
        PasswordPolicy.validate(newPassword);
        if (secret == null || secret.isBlank()) {
            throw invalidLink();
        }
        Instant now = clock.instant();
        PasswordResetToken token = resetTokens.findByHashForUpdate(SecureTokens.hash(secret))
                .filter(t -> t.isUsable(now))
                .orElseThrow(PasswordResetService::invalidLink);
        User user = users.findById(token.userId())
                .filter(u -> !u.isDeleted())
                .orElseThrow(PasswordResetService::invalidLink);

        users.save(user.withPasswordHash(hasher.hash(newPassword)));
        resetTokens.save(token.markUsed(now));
        resetTokens.invalidatePendingForUser(user.id(), now);
        refreshTokens.revokeAllForUser(user.id(), now);
    }

    private static DomainException invalidLink() {
        return new DomainException("password.reset.invalid",
                "El enlace no es válido o ya caducó. Pide uno nuevo.");
    }
}
