package com.plataformaempleo.api.candidate.application;

import com.plataformaempleo.api.candidate.domain.model.CvDocument;
import com.plataformaempleo.api.candidate.domain.model.PdfFile;
import com.plataformaempleo.api.candidate.domain.port.out.CvDocumentRepository;
import com.plataformaempleo.api.candidate.domain.port.out.CvStorage;
import com.plataformaempleo.api.candidate.domain.port.out.CvUsage;
import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.shared.application.Actor;
import com.plataformaempleo.common.domain.DomainException;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CV del candidato. El archivo va al almacenamiento de objetos y los metadatos a la base de datos.
 * No hay una transacción que abarque ambos, así que el orden importa:
 * <ul>
 *   <li>Subir: primero el archivo y luego el registro; si el registro falla, se borra el archivo.</li>
 *   <li>Borrar: primero el archivo y luego el registro; repetir el borrado es seguro.</li>
 * </ul>
 * Así nunca queda un PDF huérfano sin registro que permita encontrarlo y eliminarlo.
 */
public class CvService {

    /** VALOR SUPUESTO: CV por candidato. Evita abusos del almacenamiento; confirmar con el equipo. */
    public static final int MAX_PER_CANDIDATE = 5;

    private static final Logger log = LoggerFactory.getLogger(CvService.class);

    private final CvDocumentRepository documents;
    private final CvStorage storage;
    private final CvUsage usage;
    private final Clock clock;

    public CvService(CvDocumentRepository documents, CvStorage storage, CvUsage usage, Clock clock) {
        this.documents = Objects.requireNonNull(documents);
        this.storage = Objects.requireNonNull(storage);
        this.usage = Objects.requireNonNull(usage);
        this.clock = Objects.requireNonNull(clock);
    }

    public CvDocument upload(Actor actor, byte[] content) {
        UUID candidateId = actor.requireRole(Role.CANDIDATE);
        PdfFile file = PdfFile.of(content);
        if (documents.existsByCandidateAndSha256(candidateId, file.sha256())) {
            throw new DomainException("cv.duplicate", "Ya subiste este mismo archivo");
        }
        if (documents.countByCandidate(candidateId) >= MAX_PER_CANDIDATE) {
            throw new DomainException("cv.limit.reached",
                    "Puedes tener hasta " + MAX_PER_CANDIDATE + " CV. Elimina uno para subir otro");
        }

        CvDocument document = CvDocument.uploaded(candidateId, file, clock.instant());
        storage.put(document.storageKey(), file.content(), PdfFile.MIME);
        try {
            return documents.save(document);
        } catch (RuntimeException e) {
            deleteQuietly(document.storageKey());
            throw e;
        }
    }

    public List<CvDocument> list(Actor actor) {
        return documents.findByCandidate(actor.requireRole(Role.CANDIDATE));
    }

    public void delete(Actor actor, UUID cvId) {
        UUID candidateId = actor.requireRole(Role.CANDIDATE);
        // Un CV de otra persona responde "no existe": nunca se revela que existe.
        CvDocument document = documents.findById(cvId)
                .filter(d -> d.belongsTo(candidateId))
                .orElseThrow(() -> new DomainException("cv.not-found", "El CV no existe"));
        // Se comprueba ANTES de tocar el almacenamiento: si no, el archivo se borraría y el registro no.
        if (usage.isInUse(document.id())) {
            throw new DomainException("cv.in-use", "Este CV está en una postulación. Retírala antes de eliminarlo");
        }
        storage.delete(document.storageKey());
        documents.delete(document.id());
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException cleanupError) {
            // Solo la llave aleatoria: los registros no llevan datos personales.
            log.error("No se pudo borrar el archivo {} tras fallar su registro; requiere limpieza", key, cleanupError);
        }
    }
}
