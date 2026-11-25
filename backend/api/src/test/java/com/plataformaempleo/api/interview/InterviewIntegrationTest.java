package com.plataformaempleo.api.interview;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataformaempleo.api.TestcontainersConfig;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class InterviewIntegrationTest {
    private static final String PASSWORD = "Clave-sintetica-entrevistas";
    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    private record Fixture(String admin, String candidate, String other, String applicationId) { }

    @Test
    void agendaSinDuplicadosConPermisosYLecturasPorVersion() throws Exception {
        Fixture f = fixture();
        UUID id = UUID.randomUUID();
        Map<String, Object> details = details(1);
        Map<String, Object> body = Map.of("id", id, "applicationId", f.applicationId(), "details", details);
        assertThat(send(HttpMethod.POST, "/api/v1/interviews", body, f.admin()).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(send(HttpMethod.POST, "/api/v1/interviews", body, f.admin()).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(jdbc.queryForObject("select count(*) from interview_events where interview_id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(unread(f.candidate())).isEqualTo(1);
        assertThat(unread(f.admin())).isZero();
        assertThat(unread(f.other())).isZero();
        assertThat(send(HttpMethod.POST, "/api/v1/interviews", body, f.candidate()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(send(HttpMethod.GET, "/api/v1/interviews/" + id + "/history", null, f.other()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode listed = tree(send(HttpMethod.GET, "/api/v1/interviews", null, f.candidate()));
        assertThat(listed.get("items")).hasSize(1);
        assertThat(listed.get("items").get(0).get("interview").get("zoneId").asText()).isEqualTo("America/Guayaquil");
        JsonNode event = json.readTree(jdbc.queryForObject("select payload::text from outbox_events where aggregate_id = ?", String.class, id));
        assertThat(event.has("place")).isFalse();
        assertThat(event.has("note")).isFalse();

        String path = "/api/v1/interviews/" + id;
        assertThat(send(HttpMethod.POST, path + "/response", Map.of("expectedVersion", 0, "status", "CONFIRMED"), f.candidate()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unread(f.admin())).isEqualTo(1);
        assertThat(unread(f.candidate())).isZero();
        assertThat(send(HttpMethod.PUT, path, Map.of("expectedVersion", 1, "details", details(2)), f.admin()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unread(f.candidate())).isEqualTo(1);
        assertThat(send(HttpMethod.POST, path + "/read", Map.of("expectedVersion", 1), f.candidate()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(unread(f.candidate())).isEqualTo(1);
        assertThat(send(HttpMethod.POST, path + "/read", Map.of("expectedVersion", 100), f.candidate()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.POST, path + "/response", Map.of("expectedVersion", 1, "status", "CONFIRMED"), f.candidate()).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var confirm = CompletableFuture.supplyAsync(() -> send(HttpMethod.POST, path + "/response", Map.of("expectedVersion", 2, "status", "CONFIRMED"), f.candidate()));
        var decline = CompletableFuture.supplyAsync(() -> send(HttpMethod.POST, path + "/response", Map.of("expectedVersion", 2, "status", "DECLINED"), f.candidate()));
        assertThat(List.of(confirm.get().getStatusCode().value(), decline.get().getStatusCode().value())).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("select count(*) from interview_events where interview_id = ?", Integer.class, id)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id = ?", Integer.class, id)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select stage from applications where id = ?", String.class, UUID.fromString(f.applicationId()))).isEqualTo("RECEIVED");
        assertThat(send(HttpMethod.DELETE, "/api/v1/candidates/me/applications/" + f.applicationId(), null, f.candidate()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject("select count(*) from interviews where id = ?", Integer.class, id)).isZero();
    }

    @Test
    void cancelarYRetirarConsentimientoNoFiltraDatosAOtraEmpresa() throws Exception {
        Fixture f = fixture();
        UUID id = UUID.randomUUID();
        send(HttpMethod.POST, "/api/v1/interviews", Map.of("id", id, "applicationId", f.applicationId(), "details", details(1)), f.admin());
        String path = "/api/v1/interviews/" + id;
        String foreignAdmin = company();
        assertThat(send(HttpMethod.GET, path + "/history", null, foreignAdmin).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(send(HttpMethod.POST, path + "/cancel", Map.of("expectedVersion", 0, "reason", "Motivo sintético"), f.candidate()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(send(HttpMethod.POST, path + "/cancel", Map.of("expectedVersion", 0, "reason", "Motivo sintético"), foreignAdmin).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        send(HttpMethod.PUT, "/api/v1/candidates/me/consents", Map.of("shareWithRecruiters", false, "githubLink", false), f.candidate());
        assertThat(tree(send(HttpMethod.GET, "/api/v1/interviews", null, f.admin())).get("items")).isEmpty();
        assertThat(send(HttpMethod.PUT, path, Map.of("expectedVersion", 0, "details", details(2)), f.admin()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(send(HttpMethod.POST, path + "/read", Map.of("expectedVersion", 0), f.candidate()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        send(HttpMethod.PUT, "/api/v1/candidates/me/consents", Map.of("shareWithRecruiters", true, "githubLink", false), f.candidate());
        assertThat(send(HttpMethod.POST, path + "/cancel", Map.of("expectedVersion", 0, "reason", "Motivo sintético"), f.admin()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unread(f.candidate())).isEqualTo(1);
        assertThat(tree(send(HttpMethod.GET, "/api/v1/interviews?view=cancelled", null, f.candidate())).get("items")).hasSize(1);
        assertThat(tree(send(HttpMethod.GET, "/api/v1/interviews?view=history", null, f.candidate())).get("items")).isEmpty();
        assertThat(send(HttpMethod.POST, path + "/response", Map.of("expectedVersion", 1, "status", "CONFIRMED"), f.candidate()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void respuestasRepetidasSonIdempotentesYElMensajeNoQuitaLaConfirmacion() throws Exception {
        Fixture f = fixture();
        UUID id = schedule(f, details(2));
        String path = "/api/v1/interviews/" + id;
        var confirm = Map.of("expectedVersion", 0, "status", "CONFIRMED");
        assertThat(send(HttpMethod.POST, path + "/response", confirm, f.candidate()).getStatusCode()).isEqualTo(HttpStatus.OK);
        // E1: la segunda confirmación (doble clic u otro dispositivo, con la versión vieja) no es un error ni un aviso nuevo.
        var again = send(HttpMethod.POST, path + "/response", confirm, f.candidate());
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tree(again).get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(events(id)).isEqualTo(2);

        // E8: cambiar solo el mensaje conserva la confirmación; cambiar el lugar pide confirmar de nuevo.
        Map<String, Object> noteOnly = new java.util.HashMap<>(details(2));
        noteOnly.put("startsAt", tree(send(HttpMethod.GET, path, null, f.admin())).get("interview").get("startsAt").asText());
        noteOnly.put("note", "Otro mensaje sintético");
        assertThat(status(send(HttpMethod.PUT, path, Map.of("expectedVersion", 1, "details", noteOnly), f.admin()))).isEqualTo("CONFIRMED");
        Map<String, Object> moved = new java.util.HashMap<>(noteOnly);
        moved.put("place", "https://meet.example.test/otra-sala");
        assertThat(status(send(HttpMethod.PUT, path, Map.of("expectedVersion", 2, "details", moved, "reason", "Cambió la sala"), f.admin()))).isEqualTo("INVITED");
        assertThat(status(send(HttpMethod.POST, path + "/response", Map.of("expectedVersion", 3, "status", "CONFIRMED"), f.candidate()))).isEqualTo("CONFIRMED");

        // Tras confirmar, el candidato todavía puede avisar que no puede asistir, con motivo.
        assertThat(status(send(HttpMethod.POST, path + "/response", Map.of("expectedVersion", 4, "status", "DECLINED", "reason", "Me enfermé"), f.candidate()))).isEqualTo("DECLINED");
        // E7: después, la empresa puede proponer otra hora.
        Map<String, Object> later = new java.util.HashMap<>(moved);
        later.put("startsAt", Instant.now().plus(4, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString());
        assertThat(status(send(HttpMethod.PUT, path, Map.of("expectedVersion", 5, "details", later), f.admin()))).isEqualTo("INVITED");

        // E11: el historial dice de qué lado fue cada cambio y su motivo, sin identificar personas.
        JsonNode history = tree(send(HttpMethod.GET, path + "/history", null, f.candidate()));
        assertThat(history).hasSize(7);
        assertThat(history.get(1).get("actor").asText()).isEqualTo("CANDIDATE");
        assertThat(history.get(3).get("reason").asText()).isEqualTo("Cambió la sala");
        assertThat(history.get(3).get("placeChanged").asBoolean()).isTrue();
        assertThat(history.get(6).get("actor").asText()).isEqualTo("COMPANY");
        assertThat(history.toString()).doesNotContain("changedBy", f.applicationId());

        // E4: con el proceso cerrado el candidato ya no puede confirmar.
        jdbc.update("update applications set stage = 'REJECTED' where id = ?", UUID.fromString(f.applicationId()));
        var closed = send(HttpMethod.POST, path + "/response", Map.of("expectedVersion", 6, "status", "CONFIRMED"), f.candidate());
        assertThat(closed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tree(closed).get("code").asText()).isEqualTo("interview.closed");
    }

    @Test
    void propuestaDeOtraHoraUnaSolaAbiertaYLaEmpresaLaAceptaOLaRechaza() throws Exception {
        Fixture f = fixture();
        UUID id = schedule(f, details(3));
        String path = "/api/v1/interviews/" + id;
        String slotA = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
        String slotB = Instant.now().plus(6, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        // Dos propuestas distintas a la vez: solo una queda abierta.
        var one = CompletableFuture.supplyAsync(() -> send(HttpMethod.POST, path + "/change-requests",
                Map.of("id", first, "expectedVersion", 0, "reason", "Tengo un examen", "slots", List.of(slotA, slotB)), f.candidate()));
        var two = CompletableFuture.supplyAsync(() -> send(HttpMethod.POST, path + "/change-requests",
                Map.of("id", second, "expectedVersion", 0, "reason", "Tengo un examen", "slots", List.of(slotA)), f.candidate()));
        assertThat(List.of(one.get().getStatusCode().value(), two.get().getStatusCode().value())).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("select count(*) from interview_change_requests where interview_id = ? and status = 'OPEN'", Integer.class, id)).isEqualTo(1);
        UUID open = jdbc.queryForObject("select id from interview_change_requests where interview_id = ? and status = 'OPEN'", UUID.class, id);

        JsonNode seen = tree(send(HttpMethod.GET, path, null, f.admin()));
        assertThat(seen.get("changeRequest").get("reason").asText()).isEqualTo("Tengo un examen");
        assertThat(seen.get("interview").get("status").asText()).isEqualTo("INVITED");
        assertThat(seen.get("unread").asBoolean()).isTrue();
        assertThat(tree(send(HttpMethod.GET, "/api/v1/interviews/summary", null, f.admin())).get("counts").get("changeRequests").asLong()).isEqualTo(1);
        // E10: el candidato también la consulta por id; otra persona no.
        assertThat(send(HttpMethod.GET, path, null, f.candidate()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(send(HttpMethod.GET, path, null, f.other()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(tree(send(HttpMethod.GET, path, null, f.candidate())).get("candidateEmail").isNull()).isTrue();
        // Archivo .ics: solo para quien participa, sin caché y con la versión actual como secuencia.
        var ics = send(HttpMethod.GET, path + "/calendar", null, f.candidate());
        assertThat(ics.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ics.getHeaders().getContentType().toString()).startsWith("text/calendar");
        assertThat(ics.getHeaders().getContentDisposition().getFilename()).isEqualTo("entrevista-worksy.ics");
        assertThat(ics.getHeaders().getCacheControl()).contains("no-store");
        assertThat(ics.getBody()).contains("UID:" + id + "@worksy", "SEQUENCE:" + seen.get("interview").get("version").asInt());
        assertThat(send(HttpMethod.GET, path + "/calendar", null, f.other()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        int version = seen.get("interview").get("version").asInt();
        // Aceptar cambia la hora y deja la cita confirmada; la propuesta se cierra.
        JsonNode accepted = tree(send(HttpMethod.POST, path + "/change-requests/accept", Map.of("requestId", open, "expectedVersion", version, "slot", 0), f.admin()));
        assertThat(accepted.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(Instant.parse(accepted.get("startsAt").asText())).isEqualTo(Instant.parse(slotA));
        assertThat(send(HttpMethod.POST, path + "/change-requests/accept", Map.of("requestId", open, "expectedVersion", version + 1, "slot", 0), f.admin())
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Nueva propuesta, que la empresa rechaza manteniendo la hora.
        UUID third = UUID.randomUUID();
        int v = accepted.get("version").asInt();
        assertThat(send(HttpMethod.POST, path + "/change-requests", Map.of("id", third, "expectedVersion", v, "reason", "Otro motivo", "slots", List.of(slotB)), f.candidate())
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        // Reenviar la misma propuesta no crea otra.
        assertThat(send(HttpMethod.POST, path + "/change-requests", Map.of("id", third, "expectedVersion", v, "reason", "Otro motivo", "slots", List.of(slotB)), f.candidate())
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode kept = tree(send(HttpMethod.POST, path + "/change-requests/reject", Map.of("requestId", third, "expectedVersion", v + 1, "reason", "No tenemos esa hora"), f.admin()));
        assertThat(kept.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("select status from interview_change_requests where id = ?", String.class, third)).isEqualTo("REJECTED");
        assertThat(send(HttpMethod.POST, path + "/change-requests/accept", Map.of("requestId", third, "expectedVersion", v + 2, "slot", 0), f.candidate())
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void bloqueaCrucesDeLaMismaPostulacionYAvisaLosDelResponsable() throws Exception {
        Fixture f = fixture();
        Map<String, Object> at = details(7);
        UUID id = schedule(f, at);
        // E3: otra cita de la misma postulación a la misma hora no se agenda.
        var overlapping = send(HttpMethod.POST, "/api/v1/interviews", Map.of("id", UUID.randomUUID(), "applicationId", f.applicationId(), "details", at), f.admin());
        assertThat(overlapping.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(tree(overlapping).get("code").asText()).isEqualTo("interview.overlap");
        // Quien agenda ve sus otras citas que se cruzan (aviso), y no las suyas al excluirla.
        String start = (String) at.get("startsAt");
        JsonNode warn = tree(send(HttpMethod.GET, "/api/v1/interviews/overlaps?startsAt=" + start + "&durationMinutes=45", null, f.admin()));
        assertThat(warn).hasSize(1);
        assertThat(warn.get(0).get("interviewId").asText()).isEqualTo(id.toString());
        assertThat(tree(send(HttpMethod.GET, "/api/v1/interviews/overlaps?startsAt=" + start + "&durationMinutes=45&exclude=" + id, null, f.admin()))).isEmpty();
        assertThat(send(HttpMethod.GET, "/api/v1/interviews/overlaps?startsAt=" + start + "&durationMinutes=45", null, f.candidate()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(tree(send(HttpMethod.GET, "/api/v1/interviews?view=upcoming&mine=true", null, f.admin())).get("items")).hasSize(1);
        assertThat(tree(send(HttpMethod.GET, "/api/v1/interviews?view=pending", null, f.candidate())).get("items")).hasSize(1);

        // Calendario: rango acotado.
        Instant from = Instant.now();
        JsonNode range = tree(send(HttpMethod.GET, "/api/v1/interviews?view=range&from=" + from + "&to=" + from.plus(14, ChronoUnit.DAYS), null, f.candidate()));
        assertThat(range.get("items")).hasSize(1);
        assertThat(send(HttpMethod.GET, "/api/v1/interviews?view=range&from=" + from + "&to=" + from.plus(60, ChronoUnit.DAYS), null, f.candidate())
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void elResultadoSeRegistraDespuesDeLaHoraYLaInvitacionSinRespuestaQuedaVencida() throws Exception {
        Fixture f = fixture();
        UUID confirmed = schedule(f, details(1));
        UUID unanswered = schedule(f, details(2));
        send(HttpMethod.POST, "/api/v1/interviews/" + confirmed + "/response", Map.of("expectedVersion", 0, "status", "CONFIRMED"), f.candidate());
        String outcome = "/api/v1/interviews/" + confirmed + "/outcome";
        assertThat(send(HttpMethod.POST, outcome, Map.of("expectedVersion", 1, "outcome", "COMPLETED"), f.admin()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // Simula que ambas citas ya empezaron.
        jdbc.update("update interviews set starts_at = now() - interval '2 hours' where id in (?, ?)", confirmed, unanswered);
        assertThat(tree(send(HttpMethod.GET, "/api/v1/interviews/" + unanswered, null, f.candidate())).get("phase").asText()).isEqualTo("EXPIRED");
        assertThat(tree(send(HttpMethod.GET, "/api/v1/interviews/" + confirmed, null, f.admin())).get("phase").asText()).isEqualTo("AWAITING_OUTCOME");
        JsonNode counts = tree(send(HttpMethod.GET, "/api/v1/interviews/summary", null, f.admin())).get("counts");
        assertThat(counts.get("awaitingOutcome").asLong()).isEqualTo(1);
        assertThat(counts.get("expired").asLong()).isEqualTo(1);
        assertThat(send(HttpMethod.POST, outcome, Map.of("expectedVersion", 1, "outcome", "NO_SHOW"), f.candidate()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status(send(HttpMethod.POST, outcome, Map.of("expectedVersion", 1, "outcome", "NO_SHOW"), f.admin()))).isEqualTo("NO_SHOW");
        assertThat(status(send(HttpMethod.POST, outcome, Map.of("expectedVersion", 1, "outcome", "NO_SHOW"), f.admin()))).isEqualTo("NO_SHOW");
        assertThat(status(send(HttpMethod.POST, outcome, Map.of("expectedVersion", 2, "outcome", "COMPLETED"), f.admin()))).isEqualTo("COMPLETED");
        // Registrar el resultado no cambia la etapa del proceso.
        assertThat(jdbc.queryForObject("select stage from applications where id = ?", String.class, UUID.fromString(f.applicationId()))).isEqualTo("RECEIVED");
        assertThat(send(HttpMethod.POST, "/api/v1/interviews/" + unanswered + "/outcome", Map.of("expectedVersion", 0, "outcome", "NO_SHOW"), f.admin())
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tree(send(HttpMethod.GET, "/api/v1/interviews?view=history", null, f.candidate())).get("items")).hasSize(2);
    }

    private UUID schedule(Fixture f, Map<String, Object> details) {
        UUID id = UUID.randomUUID();
        var created = send(HttpMethod.POST, "/api/v1/interviews", Map.of("id", id, "applicationId", f.applicationId(), "details", details), f.admin());
        assertThat(created.getStatusCode()).as(created.getBody()).isEqualTo(HttpStatus.CREATED);
        return id;
    }

    private String status(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
        return tree(response).get("status").asText();
    }

    private int events(UUID id) {
        return jdbc.queryForObject("select count(*) from interview_events where interview_id = ?", Integer.class, id);
    }

    private Map<String, Object> details(int days) {
        return Map.of("title", "Entrevista sintética", "startsAt", Instant.now().plus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString(),
                "durationMinutes", 30, "zoneId", "America/Guayaquil", "mode", "ONLINE", "place", "https://meet.example.test/demo", "note", "Mensaje público sintético");
    }

    @Test
    void paginaSinOmitirCitasYBloqueaNuevasInvitacionesEnProcesosFinalizados() throws Exception {
        Fixture f = fixture();
        UUID first = UUID.randomUUID();
        Map<String, Object> firstDetails = details(1);
        for (int index = 0; index < 21; index++) {
            UUID id = index == 0 ? first : UUID.randomUUID();
            assertThat(send(HttpMethod.POST, "/api/v1/interviews", Map.of("id", id, "applicationId", f.applicationId(),
                    "details", index == 0 ? firstDetails : details(index + 1)), f.admin()).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
        JsonNode pageOne = tree(send(HttpMethod.GET, "/api/v1/interviews", null, f.candidate()));
        JsonNode pageTwo = tree(send(HttpMethod.GET, "/api/v1/interviews?page=1", null, f.candidate()));
        assertThat(pageOne.get("items")).hasSize(20);
        assertThat(pageOne.get("hasMore").asBoolean()).isTrue();
        assertThat(pageTwo.get("items")).hasSize(1);
        assertThat(pageTwo.get("hasMore").asBoolean()).isFalse();
        var ids = new java.util.HashSet<String>();
        for (JsonNode item : pageOne.get("items")) ids.add(item.get("interview").get("id").asText());
        for (JsonNode item : pageTwo.get("items")) ids.add(item.get("interview").get("id").asText());
        assertThat(ids).hasSize(21);
        assertThat(unread(f.candidate())).isEqualTo(21);
        assertThat(send(HttpMethod.POST, "/api/v1/interviews", Map.of("id", first, "applicationId", f.applicationId(), "details", details(30)), f.admin()).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(send(HttpMethod.GET, "/api/v1/interviews?page=-1", null, f.candidate()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        jdbc.update("update applications set stage = 'HIRED' where id = ?", UUID.fromString(f.applicationId()));
        assertThat(send(HttpMethod.GET, "/api/v1/interviews/context/" + f.applicationId(), null, f.admin()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.POST, "/api/v1/interviews", Map.of("id", UUID.randomUUID(), "applicationId", f.applicationId(), "details", details(1)), f.admin()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.PUT, "/api/v1/interviews/" + first, Map.of("expectedVersion", 0, "details", details(2)), f.admin()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.POST, "/api/v1/interviews/" + first + "/cancel", Map.of("expectedVersion", 0, "reason", "Motivo sintético"), f.admin()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private Fixture fixture() throws Exception {
        String admin = company();
        String candidateEmail = "c-" + UUID.randomUUID() + "@ejemplo.test";
        String otherEmail = "c-" + UUID.randomUUID() + "@ejemplo.test";
        for (String email : List.of(candidateEmail, otherEmail)) send(HttpMethod.POST, "/api/v1/auth/register", Map.of("email", email, "password", PASSWORD), null);
        String candidate = login(candidateEmail);
        String other = login(otherEmail);
        UUID user = jdbc.queryForObject("select id from users where email = ?", UUID.class, candidateEmail);
        UUID cv = UUID.randomUUID();
        jdbc.update("insert into cv_documents (id,candidate_id,storage_key,sha256,mime,size_bytes,status,uploaded_at) values (?,?,?,?,'application/pdf',100,'UPLOADED',?)",
                cv, user, "cv/" + cv + ".pdf", cv.toString().replace("-", "").repeat(2), Timestamp.from(Instant.now()));
        send(HttpMethod.PUT, "/api/v1/candidates/me/consents", Map.of("shareWithRecruiters", true, "githubLink", false), candidate);
        var createdVacancy = send(HttpMethod.POST, "/api/v1/companies/me/vacancies", Map.of("title", "Vacante sintética", "description", "Equipo de desarrollo backend Java.", "location", "Quito",
                "modality", "REMOTO", "criteria", List.of(Map.of("label", "Java", "question", "¿Tiene experiencia profesional con Java?", "kind", "YES_NO"))), admin);
        assertThat(createdVacancy.getStatusCode()).as(createdVacancy.getBody()).isEqualTo(HttpStatus.CREATED);
        JsonNode vacancy = tree(createdVacancy);
        String vacancyId = vacancy.get("id").asText();
        send(HttpMethod.POST, "/api/v1/companies/me/vacancies/" + vacancyId + "/publish", null, admin);
        String application = tree(send(HttpMethod.POST, "/api/v1/vacancies/" + vacancyId + "/applications", Map.of("cvId", cv), candidate)).get("id").asText();
        return new Fixture(admin, candidate, other, application);
    }

    private String company() throws Exception {
        String email = "e-" + UUID.randomUUID() + "@ejemplo.test";
        send(HttpMethod.POST, "/api/v1/companies", Map.of("companyName", "Empresa sintética", "email", email, "password", PASSWORD), null);
        return login(email);
    }
    private String login(String email) throws Exception { return tree(send(HttpMethod.POST, "/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), null)).get("accessToken").asText(); }
    private long unread(String token) throws Exception { return tree(send(HttpMethod.GET, "/api/v1/interviews/unread-count", null, token)).get("unread").asLong(); }
    private JsonNode tree(ResponseEntity<String> result) throws Exception { return json.readTree(result.getBody()); }
    private ResponseEntity<String> send(HttpMethod method, String path, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }
}
