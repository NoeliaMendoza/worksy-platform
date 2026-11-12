package com.plataformaempleo.worker.screening.infrastructure.inference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataformaempleo.worker.screening.domain.model.Criterion;
import com.plataformaempleo.worker.screening.domain.model.ScreeningFailure;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Contrato con el servicio de inferencia contra un servidor HTTP local de prueba. */
class HttpDecisionProviderTest {

    private final ObjectMapper json = new ObjectMapper();
    private final List<JsonNode> requests = new ArrayList<>();
    private HttpServer server;

    private final List<Criterion> criteria = List.of(
            new Criterion("q1", "Spring Boot", "¿Tiene experiencia con Spring Boot?", Criterion.Kind.YES_NO,
                    List.of("Sí", "No")),
            new Criterion("q2", "Años en Java", "¿Cuántos años con Java?", Criterion.Kind.CHOICE,
                    List.of("Más de 3", "1 a 3", "Menos de 1")));

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void enviaElContratoDeLaSeccion7YLeeLaVersionDelModelo() throws Exception {
        HttpDecisionProvider provider = provider(new int[] {200});

        var decision = provider.decide("baseline", "texto del CV", "Backend Java", criteria);

        JsonNode sent = requests.get(0);
        assertThat(sent.get("provider").asText()).isEqualTo("baseline");
        assertThat(sent.get("state").get("cv_text").asText()).isEqualTo("texto del CV");
        assertThat(sent.get("state").get("job_description").asText()).isEqualTo("Backend Java");
        JsonNode q1 = sent.get("questions").get("q1");
        assertThat(q1.get("type").asText()).isEqualTo("choice");
        assertThat(q1.get("instructions").asText()).isEqualTo("¿Tiene experiencia con Spring Boot?");
        // La primera opción es la afirmativa y describe la evidencia buscada.
        assertThat(q1.get("criteria").fieldNames()).toIterable().containsExactly("Sí", "No");
        assertThat(sent.get("questions").get("q2").get("criteria")).hasSize(3);

        assertThat(decision.answers().get("q1").choice()).isEqualTo("Sí");
        assertThat(decision.modelVersion()).isEqualTo("baseline-bow-1");
    }

    @Test
    void reintentaUn503YTerminaBien() throws Exception {
        HttpDecisionProvider provider = provider(new int[] {503, 200});

        assertThat(provider.decide("baseline", "texto", "", criteria).answers()).containsKey("q2");
        assertThat(requests).hasSize(2);
    }

    @Test
    void trasTresFallosEsUnFalloPasajero() throws Exception {
        HttpDecisionProvider provider = provider(new int[] {503, 503, 503});

        assertThatThrownBy(() -> provider.decide("baseline", "texto", "", criteria))
                .isInstanceOfSatisfying(ScreeningFailure.class, f -> assertThat(f.retryable()).isTrue());
        assertThat(requests).hasSize(3);
    }

    @Test
    void unRechazo400NoSeReintenta() throws Exception {
        HttpDecisionProvider provider = provider(new int[] {400});

        assertThatThrownBy(() -> provider.decide("baseline", "texto", "", criteria))
                .isInstanceOfSatisfying(ScreeningFailure.class, f -> assertThat(f.retryable()).isFalse());
        assertThat(requests).hasSize(1);
    }

    @Test
    void unProveedorNoImplementado501NoSeReintenta() throws Exception {
        HttpDecisionProvider provider = provider(new int[] {501});

        assertThatThrownBy(() -> provider.decide("laya", "texto", "", criteria))
                .isInstanceOfSatisfying(ScreeningFailure.class, f -> assertThat(f.retryable()).isFalse());
        assertThat(requests).hasSize(1);
    }

    /** Servidor que responde con los códigos indicados, uno por petición. */
    private HttpDecisionProvider provider(int[] statuses) throws IOException {
        AtomicInteger call = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/decide", exchange -> {
            requests.add(json.readTree(exchange.getRequestBody()));
            int status = statuses[Math.min(call.getAndIncrement(), statuses.length - 1)];
            byte[] body = (status == 200
                    ? "{\"answers\":{\"q1\":{\"choice\":\"Sí\",\"probability\":0.9},"
                    + "\"q2\":{\"choice\":\"1 a 3\",\"probability\":0.7}},\"model_version\":\"baseline-bow-1\",\"latency_ms\":3}"
                    : "{\"title\":\"error\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            if (status == 200) {
                exchange.getResponseHeaders().add("X-Model-Version", "baseline-bow-1");
            }
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        var factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(5));
        RestClient http = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .requestFactory(factory)
                .build();
        return new HttpDecisionProvider(http, Duration.ofMillis(10));
    }
}
