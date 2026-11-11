package com.plataformaempleo.api.candidate.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plataformaempleo.api.candidate.SyntheticPdf;
import com.plataformaempleo.api.candidate.domain.model.CvDocument;
import com.plataformaempleo.api.candidate.domain.port.out.CvDocumentRepository;
import com.plataformaempleo.api.candidate.domain.port.out.CvStorage;
import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.shared.application.Actor;
import com.plataformaempleo.common.domain.DomainException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Reglas del caso de uso con adaptadores en memoria (sin Spring, sin Docker). */
class CvServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-11T10:00:00Z"), ZoneOffset.UTC);
    private final InMemoryDocuments documents = new InMemoryDocuments();
    private final InMemoryStorage storage = new InMemoryStorage();
    private final CvService service = new CvService(documents, storage, clock);

    private final Actor candidate = new Actor(UUID.randomUUID(), Role.CANDIDATE, null);

    @Test
    void subeElArchivoAlAlmacenamientoYRegistraSusMetadatos() {
        CvDocument doc = service.upload(candidate, SyntheticPdf.of("1"));

        assertThat(doc.candidateId()).isEqualTo(candidate.userId());
        assertThat(doc.status()).isEqualTo(CvDocument.Status.UPLOADED);
        assertThat(doc.uploadedAt()).isEqualTo(clock.instant());
        assertThat(doc.storageKey()).startsWith("cv/").doesNotContain(candidate.userId().toString());
        assertThat(storage.objects).containsKey(doc.storageKey());
        assertThat(service.list(candidate)).extracting(CvDocument::id).containsExactly(doc.id());
    }

    @Test
    void rechazaElMismoArchivoDosVecesSinTocarElAlmacenamiento() {
        service.upload(candidate, SyntheticPdf.of("dup"));

        assertCode(() -> service.upload(candidate, SyntheticPdf.of("dup")), "cv.duplicate");
        assertThat(storage.objects).hasSize(1);
    }

    @Test
    void otroCandidatoPuedeSubirElMismoArchivo() {
        Actor other = new Actor(UUID.randomUUID(), Role.CANDIDATE, null);
        service.upload(candidate, SyntheticPdf.of("plantilla"));

        assertThat(service.upload(other, SyntheticPdf.of("plantilla")).candidateId()).isEqualTo(other.userId());
    }

    @Test
    void limitaLaCantidadDeCvPorCandidato() {
        for (int i = 0; i < CvService.MAX_PER_CANDIDATE; i++) {
            service.upload(candidate, SyntheticPdf.of("n" + i));
        }
        assertCode(() -> service.upload(candidate, SyntheticPdf.of("uno-mas")), "cv.limit.reached");
    }

    @Test
    void siFallaElRegistroBorraElArchivoParaNoDejarHuerfanos() {
        documents.failOnSave = true;

        assertThatThrownBy(() -> service.upload(candidate, SyntheticPdf.of("x"))).isInstanceOf(IllegalStateException.class);
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void siFallaElAlmacenamientoNoSeRegistraNada() {
        storage.failOnPut = true;

        assertCode(() -> service.upload(candidate, SyntheticPdf.of("x")), "storage.unavailable");
        assertThat(documents.rows).isEmpty();
    }

    @Test
    void eliminarBorraArchivoYRegistro() {
        CvDocument doc = service.upload(candidate, SyntheticPdf.of("borrar"));

        service.delete(candidate, doc.id());

        assertThat(storage.objects).isEmpty();
        assertThat(documents.rows).isEmpty();
    }

    @Test
    void siFallaElAlmacenamientoAlEliminarSeConservaElRegistroParaReintentar() {
        CvDocument doc = service.upload(candidate, SyntheticPdf.of("reintento"));
        storage.failOnDelete = true;

        assertCode(() -> service.delete(candidate, doc.id()), "storage.unavailable");
        assertThat(documents.rows).containsKey(doc.id());
    }

    @Test
    void elCvDeOtraPersonaNoExisteParaQuienNoEsSuDueno() {
        CvDocument doc = service.upload(candidate, SyntheticPdf.of("ajeno"));
        Actor intruder = new Actor(UUID.randomUUID(), Role.CANDIDATE, null);

        assertCode(() -> service.delete(intruder, doc.id()), "cv.not-found");
        assertThat(storage.objects).containsKey(doc.storageKey());
    }

    @Test
    void soloLosCandidatosTienenCv() {
        Actor recruiter = new Actor(UUID.randomUUID(), Role.RECRUITER, UUID.randomUUID());

        assertCode(() -> service.upload(recruiter, SyntheticPdf.of("r")), "access.denied");
        assertCode(() -> service.list(recruiter), "access.denied");
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(code);
    }

    // ---- adaptadores en memoria ----

    private static final class InMemoryStorage implements CvStorage {
        final Map<String, byte[]> objects = new HashMap<>();
        boolean failOnPut;
        boolean failOnDelete;

        @Override
        public void put(String key, byte[] content, String contentType) {
            if (failOnPut) {
                throw new DomainException("storage.unavailable", "caído");
            }
            objects.put(key, content);
        }

        @Override
        public void delete(String key) {
            if (failOnDelete) {
                throw new DomainException("storage.unavailable", "caído");
            }
            objects.remove(key);
        }
    }

    private static final class InMemoryDocuments implements CvDocumentRepository {
        final Map<UUID, CvDocument> rows = new LinkedHashMap<>();
        boolean failOnSave;

        @Override
        public CvDocument save(CvDocument d) {
            if (failOnSave) {
                throw new IllegalStateException("base de datos caída");
            }
            rows.put(d.id(), d);
            return d;
        }

        @Override
        public Optional<CvDocument> findById(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public List<CvDocument> findByCandidate(UUID candidateId) {
            List<CvDocument> result = new ArrayList<>(rows.values().stream().filter(d -> d.belongsTo(candidateId)).toList());
            result.sort(Comparator.comparing(CvDocument::uploadedAt).reversed());
            return result;
        }

        @Override
        public boolean existsByCandidateAndSha256(UUID candidateId, String sha256) {
            return rows.values().stream().anyMatch(d -> d.belongsTo(candidateId) && d.sha256().equals(sha256));
        }

        @Override
        public long countByCandidate(UUID candidateId) {
            return rows.values().stream().filter(d -> d.belongsTo(candidateId)).count();
        }

        @Override
        public void delete(UUID id) {
            rows.remove(id);
        }
    }
}
