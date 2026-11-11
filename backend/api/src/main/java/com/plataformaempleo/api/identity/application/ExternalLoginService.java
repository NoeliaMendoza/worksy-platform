package com.plataformaempleo.api.identity.application;

import com.plataformaempleo.api.identity.application.AuthService.AuthSession;
import com.plataformaempleo.api.identity.domain.model.ExternalIdentity;
import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.identity.domain.model.SecureTokens;
import com.plataformaempleo.api.identity.domain.model.User;
import com.plataformaempleo.api.identity.domain.port.out.ExternalIdentityRepository;
import com.plataformaempleo.api.identity.domain.port.out.PasswordHasher;
import com.plataformaempleo.api.identity.domain.port.out.UserRepository;
import com.plataformaempleo.common.domain.DomainException;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inicio de sesión con una cuenta externa ya verificada por el proveedor.
 * <ol>
 *   <li>Si (proveedor, subject) ya está vinculado, entra con ese usuario.</li>
 *   <li>Si no, y el proveedor confirma que el correo está verificado, se vincula a la cuenta con ese correo
 *       o se crea una cuenta de candidato.</li>
 *   <li>Con un correo no verificado no se vincula nada: evitaría que alguien se apropie de una cuenta ajena.</li>
 * </ol>
 */
public class ExternalLoginService {

    private final UserRepository users;
    private final ExternalIdentityRepository identities;
    private final PasswordHasher hasher;
    private final AuthService auth;
    private final Clock clock;

    public ExternalLoginService(UserRepository users, ExternalIdentityRepository identities, PasswordHasher hasher,
                                AuthService auth, Clock clock) {
        this.users = Objects.requireNonNull(users);
        this.identities = Objects.requireNonNull(identities);
        this.hasher = Objects.requireNonNull(hasher);
        this.auth = Objects.requireNonNull(auth);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public AuthSession login(ExternalProfile profile) {
        var linked = identities.findByProviderAndSubject(profile.provider(), profile.subject());
        if (linked.isPresent()) {
            User user = users.findById(linked.get().userId())
                    .filter(u -> !u.isDeleted())
                    .orElseThrow(() -> new DomainException("auth.external.account-disabled",
                            "La cuenta vinculada ya no está activa"));
            return auth.startSession(user);
        }
        if (!profile.emailVerified() || profile.email() == null || profile.email().isBlank()) {
            throw new DomainException("auth.external.email-unverified",
                    "El proveedor no confirmó un correo verificado");
        }
        User user = users.findActiveByEmail(profile.email())
                .orElseGet(() -> users.save(User.register(profile.email(),
                        // Contraseña aleatoria que nadie conoce: para usar correo y contraseña, la persona la restablece.
                        hasher.hash(SecureTokens.newSecret()),
                        Role.CANDIDATE, null, clock)));
        identities.save(ExternalIdentity.link(user.id(), profile.provider(), profile.subject(), profile.email(),
                clock.instant()));
        return auth.startSession(user);
    }

    /** Lo que el adaptador de OAuth extrae del proveedor. */
    public record ExternalProfile(String provider, String subject, String email, boolean emailVerified) {
    }
}
