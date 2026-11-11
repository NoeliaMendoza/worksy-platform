package com.plataformaempleo.api.identity.domain.port.out;

import com.plataformaempleo.api.identity.domain.model.PasswordResetToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida para los enlaces de recuperación de contraseña. */
public interface PasswordResetTokenRepository {

    PasswordResetToken save(PasswordResetToken token);

    /** Busca por hash y bloquea la fila: un enlace no puede usarse dos veces en paralelo. */
    Optional<PasswordResetToken> findByHashForUpdate(String tokenHash);

    /** Marca como usados los enlaces pendientes del usuario (al pedir uno nuevo o al cambiar la contraseña). */
    int invalidatePendingForUser(UUID userId, Instant now);
}
