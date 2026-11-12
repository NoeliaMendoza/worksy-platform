package com.plataformaempleo.api.shared.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida para el encabezado Idempotency-Key: recuerda qué produjo una petición para
 * devolver lo mismo si se repite (doble clic, reintento del cliente tras un corte de red).
 */
public interface IdempotencyStore {

    /**
     * @param operation   operación protegida (p. ej., "screening.request")
     * @param fingerprint huella de la petición; la misma clave con otra petición es un error
     * @param resultIds   ids de lo que se creó, para reconstruir la respuesta
     * @param skipped     elementos que la petición original no procesó (se devuelve igual al repetirla)
     */
    record Entry(String operation, String fingerprint, List<UUID> resultIds, int skipped) {
    }

    Optional<Entry> find(UUID actorId, String key);

    void save(UUID actorId, String key, Entry entry);
}
