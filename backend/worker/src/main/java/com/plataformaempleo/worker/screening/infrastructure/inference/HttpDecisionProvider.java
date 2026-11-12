package com.plataformaempleo.worker.screening.infrastructure.inference;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.plataformaempleo.worker.screening.domain.model.Criterion;
import com.plataformaempleo.worker.screening.domain.model.Decision;
import com.plataformaempleo.worker.screening.domain.model.ScreeningFailure;
import com.plataformaempleo.worker.screening.domain.port.out.DecisionProvider;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente del servicio de inferencia (POST /v1/decide). Contrato de la sección 7 del diseño:
 * cada criterio es una pregunta {@code choice} y sus opciones son las de la vacante.
 * <ul>
 *   <li>Tiempo límite de 5 s por llamada (lo fija el RestClient).</li>
 *   <li>Hasta 3 intentos con espera creciente y aleatoria (sección 5, paso 6).</li>
 *   <li>Un 4xx no se reintenta, ni un 501 (el servicio responde 501 si el proveedor aún no está
 *       implementado, como Laya hasta verificar su API): repetir no lo arreglaría.</li>
 * </ul>
 */
public class HttpDecisionProvider implements DecisionProvider {

    public static final int MAX_ATTEMPTS = 3;
    private static final Logger log = LoggerFactory.getLogger(HttpDecisionProvider.class);

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record Question(String type, String instructions, Map<String, String> criteria) {
    }

    record State(@JsonProperty("cv_text") String cvText, @JsonProperty("job_description") String jobDescription) {
    }

    record DecideRequest(String provider, State state, Map<String, Question> questions) {
    }

    record AnswerOut(String choice, Double probability, Double score) {
    }

    record DecideResponse(Map<String, AnswerOut> answers, @JsonProperty("model_version") String modelVersion,
                          @JsonProperty("latency_ms") Integer latencyMs) {
    }

    private final RestClient http;
    private final Duration baseBackoff;

    public HttpDecisionProvider(RestClient http, Duration baseBackoff) {
        this.http = http;
        this.baseBackoff = baseBackoff;
    }

    @Override
    public Decision decide(String provider, String cvText, String jobDescription, List<Criterion> criteria) {
        DecideRequest body = new DecideRequest(provider, new State(cvText, jobDescription), questions(criteria));
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                ResponseEntity<DecideResponse> response = http.post().uri("/v1/decide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toEntity(DecideResponse.class);
                return toDecision(response);
            } catch (HttpClientErrorException e) {
                throw new ScreeningFailure("El servicio de inferencia rechazó la petición (" + e.getStatusCode().value()
                        + ")", false, e);
            } catch (HttpServerErrorException e) {
                if (e.getStatusCode().value() == 501) {
                    throw new ScreeningFailure("El proveedor de decisión no está disponible (501)", false, e);
                }
                last = e;
                log.warn("Inferencia con error {} (intento {}/{})", e.getStatusCode().value(), attempt, MAX_ATTEMPTS);
                if (attempt < MAX_ATTEMPTS) {
                    pause(attempt);
                }
            } catch (RestClientException e) {
                last = e;
                log.warn("Inferencia no disponible (intento {}/{}): {}", attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    pause(attempt);
                }
            }
        }
        throw ScreeningFailure.transientFailure("El servicio de inferencia no respondió tras " + MAX_ATTEMPTS
                + " intentos", last);
    }

    /** Espera creciente con variación aleatoria ("full jitter") para no sincronizar reintentos de varias réplicas. */
    private void pause(int attempt) {
        long cap = baseBackoff.toMillis() * (1L << (attempt - 1));
        long wait = ThreadLocalRandom.current().nextLong(cap / 2, cap + 1);
        try {
            Thread.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ScreeningFailure.transientFailure("Interrumpido mientras esperaba para reintentar", e);
        }
    }

    static Map<String, Question> questions(List<Criterion> criteria) {
        Map<String, Question> questions = new LinkedHashMap<>();
        for (Criterion c : criteria) {
            Map<String, String> options = new LinkedHashMap<>();
            if (c.kind() == Criterion.Kind.YES_NO) {
                // Convención del contrato: la primera opción es la afirmativa y describe la evidencia buscada.
                options.put(c.options().get(0), c.label());
                options.put(c.options().get(1), "Sin evidencia en el CV");
            } else {
                c.options().forEach(o -> options.put(o, o));
            }
            questions.put(c.key(), new Question("choice", c.question(), options));
        }
        return questions;
    }

    private static Decision toDecision(ResponseEntity<DecideResponse> response) {
        DecideResponse body = response.getBody();
        if (body == null || body.answers() == null) {
            throw ScreeningFailure.permanent("Respuesta vacía del servicio de inferencia");
        }
        Map<String, Decision.Answer> answers = new LinkedHashMap<>();
        body.answers().forEach((key, a) -> {
            if (a.choice() != null) {
                answers.put(key, new Decision.Answer(a.choice(), a.probability() == null ? 0 : a.probability()));
            }
        });
        String header = response.getHeaders().getFirst("X-Model-Version");
        String version = header != null ? header : body.modelVersion();
        return new Decision(answers, version == null ? "desconocida" : version,
                body.latencyMs() == null ? 0 : body.latencyMs());
    }
}
