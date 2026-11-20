package com.plataformaempleo.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.plataformaempleo.common.events.EventTypes;
import com.plataformaempleo.common.events.Messaging;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.converter.SimpleMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Tiempo real de punta a punta con un RabbitMQ real (AMQP + STOMP): token, suscripción personal y aviso.
 * Datos sintéticos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealtimeApplicationTest {

    private static final String SECRET = "clave-sintetica-solo-para-pruebas-0123456789abcdef";
    private static final String ORIGIN = "http://localhost:5173";

    /** Misma imagen que Docker Compose, con el plugin STOMP activo antes de que arranque la aplicación. */
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:4.1-management-alpine"));

    static {
        RABBIT.addExposedPort(61613);
        RABBIT.start();
        try {
            var result = RABBIT.execInContainer("rabbitmq-plugins", "enable", "rabbitmq_stomp");
            if (result.getExitCode() != 0) {
                throw new IllegalStateException("No se pudo activar STOMP: " + result.getStderr());
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("worksy.realtime.jwt-secret", () -> SECRET);
        registry.add("worksy.realtime.allowed-origins", () -> ORIGIN);
        registry.add("worksy.realtime.relay.host", RABBIT::getHost);
        registry.add("worksy.realtime.relay.port", () -> RABBIT.getMappedPort(61613));
        registry.add("worksy.realtime.relay.login", RABBIT::getAdminUsername);
        registry.add("worksy.realtime.relay.passcode", RABBIT::getAdminPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private ObjectMapper json;

    private WebSocketStompClient client;

    @AfterEach
    void stop() {
        if (client != null) {
            client.stop();
        }
    }

    @Test
    void arrancaYReportaSalud() {
        var health = http.getForEntity("/actuator/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void entregaElAvisoSoloAQuienDebeRecibirlo() throws Exception {
        UUID ana = UUID.randomUUID();
        UUID luis = UUID.randomUUID();
        BlockingQueue<String> anaInbox = subscribe(connect(token(ana, SECRET, Instant.now().plusSeconds(600))), ana);
        BlockingQueue<String> luisInbox = subscribe(connect(token(luis, SECRET, Instant.now().plusSeconds(600))), luis);

        UUID application = UUID.randomUUID();
        UUID message = UUID.randomUUID();
        // Hasta que el broker relay confirma la suscripción puede pasar un instante: se reintenta el envío.
        String received = null;
        for (int attempt = 0; attempt < 10 && received == null; attempt++) {
            publish(Map.of("messageId", message.toString(), "applicationId", application.toString(),
                    "recipientIds", List.of(ana.toString())));
            received = anaInbox.poll(1, TimeUnit.SECONDS);
        }

        assertThat(received).as("aviso para Ana").isNotNull();
        JsonNode notice = json.readTree(received);
        assertThat(notice.get("type").asText()).isEqualTo("message.sent");
        assertThat(notice.get("applicationId").asText()).isEqualTo(application.toString());
        assertThat(notice.get("messageId").asText()).isEqualTo(message.toString());
        assertThat(luisInbox.poll(1, TimeUnit.SECONDS)).as("Luis no es destinatario").isNull();
    }

    @Test
    void avisaUnCambioDeEtapaSoloAlCandidatoDestinatario() throws Exception {
        UUID ana = UUID.randomUUID();
        UUID otra = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        BlockingQueue<String> own = subscribe(connect(token(ana, SECRET, Instant.now().plusSeconds(600))), ana);
        BlockingQueue<String> other = subscribe(connect(token(otra, SECRET, Instant.now().plusSeconds(600))), otra);
        String received = null;
        for (int i = 0; i < 5 && received == null; i++) {
            publish(EventTypes.APPLICATION_STAGE_CHANGED, Map.of("applicationId", applicationId.toString(),
                    "stageChangeId", 42L, "recipientIds", List.of(ana.toString())));
            received = own.poll(1, TimeUnit.SECONDS);
        }
        assertThat(received).isNotNull();
        JsonNode notice = json.readTree(received);
        assertThat(notice.get("type").asText()).isEqualTo("application.stage.changed");
        assertThat(notice.get("stageChangeId").asLong()).isEqualTo(42L);
        assertThat(notice.get("applicationId").asText()).isEqualTo(applicationId.toString());
        assertThat(notice.has("message")).isFalse();
        assertThat(other.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void rechazaConexionesSinTokenValido() {
        UUID ana = UUID.randomUUID();
        assertThatThrownBy(() -> connect(null)).as("sin token").isNotNull();
        assertThatThrownBy(() -> connect(token(ana, "otra-clave-sintetica-de-32-caracteres-o-mas", Instant.now()
                .plusSeconds(600)))).as("firmado con otra clave").isNotNull();
        assertThatThrownBy(() -> connect(token(ana, SECRET, Instant.now().minusSeconds(120))))
                .as("vencido").isNotNull();
    }

    @Test
    void avisaEntrevistasSoloADestinatariosSinDatosDeLaCita() throws Exception {
        UUID recipient = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        UUID interview = UUID.randomUUID();
        UUID application = UUID.randomUUID();
        BlockingQueue<String> own = subscribe(connect(token(recipient, SECRET, Instant.now().plusSeconds(600))), recipient);
        BlockingQueue<String> other = subscribe(connect(token(outsider, SECRET, Instant.now().plusSeconds(600))), outsider);
        String received = null;
        for (int attempt = 0; attempt < 5 && received == null; attempt++) {
            publish(EventTypes.INTERVIEW_CHANGED, Map.of("applicationId", application.toString(), "interviewId", interview.toString(),
                    "recipientIds", List.of(recipient.toString(), recipient.toString()), "note", "Dato que no debe reenviarse", "place", "https://example.test"));
            received = own.poll(1, TimeUnit.SECONDS);
        }
        assertThat(received).isNotNull();
        JsonNode notice = json.readTree(received);
        assertThat(notice.get("type").asText()).isEqualTo("interview.changed");
        assertThat(notice.get("interviewId").asText()).isEqualTo(interview.toString());
        assertThat(notice.get("applicationId").asText()).isEqualTo(application.toString());
        assertThat(notice.has("note")).isFalse();
        assertThat(notice.has("place")).isFalse();
        assertThat(other.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void unAvisoSinObjetoSeEnviaALaColaDeFallidos() {
        String id = UUID.randomUUID().toString();
        rabbit.send(Messaging.EVENTS_EXCHANGE, EventTypes.APPLICATION_STAGE_CHANGED,
                MessageBuilder.withBody("null".getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON).setMessageId(id).build());
        var failed = rabbit.receive(Messaging.REALTIME_NOTIFICATIONS_DLQ, 10_000);
        assertThat(failed).isNotNull();
        assertThat(failed.getMessageProperties().getMessageId()).isEqualTo(id);
    }

    @Test
    void noPermiteSuscribirseALosAvisosDeOtraPersona() throws Exception {
        UUID ana = UUID.randomUUID();
        StompSession session = connect(token(ana, SECRET, Instant.now().plusSeconds(600)));
        session.subscribe("/topic/user." + UUID.randomUUID(), new Collector(new LinkedBlockingQueue<>()));

        // El realtime responde con ERROR y cierra la conexión.
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (session.isConnected() && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertThat(session.isConnected()).isFalse();
    }

    // ---- utilidades ----

    private StompSession connect(String token) throws Exception {
        client = client == null ? new WebSocketStompClient(new StandardWebSocketClient()) : client;
        client.setMessageConverter(new SimpleMessageConverter());
        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        handshake.setOrigin(ORIGIN);
        StompHeaders connect = new StompHeaders();
        if (token != null) {
            connect.add("Authorization", "Bearer " + token);
        }
        CompletableFuture<StompSession> future = client.connectAsync("ws://localhost:" + port + "/ws", handshake,
                connect, new StompSessionHandlerAdapter() { });
        StompSession session = future.get(5, TimeUnit.SECONDS);
        // Un CONNECT rechazado devuelve ERROR y cierra: esperar un momento para distinguirlo.
        Thread.sleep(300);
        if (!session.isConnected()) {
            throw new IllegalStateException("Conexión rechazada");
        }
        return session;
    }

    private BlockingQueue<String> subscribe(StompSession session, UUID userId) {
        BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
        session.subscribe("/topic/user." + userId, new Collector(inbox));
        return inbox;
    }

    private void publish(Map<String, Object> event) throws Exception {
        publish(EventTypes.MESSAGE_SENT, event);
    }

    private void publish(String eventType, Map<String, Object> event) throws Exception {
        rabbit.send(Messaging.EVENTS_EXCHANGE, eventType,
                MessageBuilder.withBody(json.writeValueAsBytes(event))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON).setMessageId(UUID.randomUUID().toString())
                        .build());
    }

    private static String token(UUID userId, String secret, Instant expiresAt) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer("worksy-api").subject(userId.toString())
                .issueTime(Date.from(expiresAt.minusSeconds(900))).expirationTime(Date.from(expiresAt))
                .claim("role", "CANDIDATE").build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    private record Collector(BlockingQueue<String> inbox) implements StompFrameHandler {
        @Override
        public Type getPayloadType(StompHeaders headers) {
            return byte[].class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            inbox.add(new String((byte[]) payload, StandardCharsets.UTF_8));
        }
    }
}
