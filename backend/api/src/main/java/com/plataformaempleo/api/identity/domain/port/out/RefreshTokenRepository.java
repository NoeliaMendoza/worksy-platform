package com.plataformaempleo.api.identity.domain.port.out;

import com.plataformaempleo.api.identity.domain.model.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida para los tokens de refresco. */
public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken token);

    /** Busca por hash y bloquea la fila hasta el fin de la transacción (evita dos rotaciones simultáneas). */
    Optional<RefreshToken> findByHashForUpdate(String tokenHash);

    /** Revoca todos los tokens activos de la familia. Devuelve cuántos revocó. */
    int revokeFamily(UUID familyId, Instant now);

    /** Revoca todas las sesiones del usuario (p. ej., tras cambiar la contraseña). */
    int revokeAllForUser(UUID userId, Instant now);
}
