package com.plataformaempleo.realtime.notification.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reglas de cada trama STOMP sin levantar el servicio. Datos sintéticos. */
class StompAuthInterceptorTest {

    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");
    private static final UUID ANA = UUID.fromString("00000000-0000-0000-0000-00000000a0a0");

    private Instant tokenExpiresAt = NOW.plusSeconds(600);
    private Instant now = NOW;
    private final Map<String, Object> session = new HashMap<>();

    private final StompAuthInterceptor interceptor = new StompAuthInterceptor(token -> {
        if (!token.equals("valido")) {
            throw new BadJwtException("firma");
        }
        return Jwt.withTokenValue(token).header("alg", "HS256").subject(ANA.toString())
                .issuedAt(tokenExpiresAt.minusSeconds(900)).expiresAt(tokenExpiresAt).build();
    }, new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    });

    @Test
    void conectaConTokenVigenteYGuardaCuandoVence() {
        var accessor = send(frame(StompCommand.CONNECT, Map.of("accept-version", "1.2", "heart-beat", "10000,10000",
                "Authorization", "Bearer valido")));
        assertThat(accessor.getUser()).isEqualTo(new StompAuthInterceptor.UserPrincipal(ANA));
        assertThat(session.get(StompAuthInterceptor.EXPIRES_AT)).isEqualTo(tokenExpiresAt);
    }

    @Test
    void rechazaTokenVencidoAunqueEsteDentroDelMargenDeRelojes() {
        tokenExpiresAt = NOW.minusSeconds(30);
        assertThatThrownBy(() -> send(frame(StompCommand.CONNECT, Map.of("Authorization", "Bearer valido"))))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> send(frame(StompCommand.CONNECT, Map.of("Authorization", "Bearer otro"))))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void permiteSuscribirseAlTemaPropioConCabecerasEstandar() {
        var user = connect();
        send(frame(StompCommand.SUBSCRIBE, Map.of("id", "sub-0", "destination", "/topic/user." + ANA), user));
        send(frame(StompCommand.SUBSCRIBE, Map.of("id", "sub-1", "destination", "/topic/user." + ANA,
                "ack", "auto", "receipt", "r-1"), user));
    }

    @Test
    void rechazaCabecerasQueEligenOCreanColasEnRabbitMq() {
        var user = connect();
        for (String header : new String[] {"x-queue-name", "durable", "auto-delete", "exclusive", "persistent",
                "x-queue-type", "x-message-ttl", "x-dead-letter-exchange", "prefetch-count", "selector"}) {
            Map<String, String> headers = new HashMap<>(Map.of("id", "sub-0", "destination", "/topic/user." + ANA));
            headers.put(header, "realtime.notifications");
            assertThatThrownBy(() -> send(frame(StompCommand.SUBSCRIBE, headers, user)))
                    .as(header).isInstanceOf(MessageDeliveryException.class);
        }
        assertThatThrownBy(() -> send(frame(StompCommand.UNSUBSCRIBE, Map.of("id", "sub-0", "durable", "true"), user)))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> send(frame(StompCommand.CONNECT, Map.of("Authorization", "Bearer valido",
                "x-extra", "1")))).isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void rechazaConfirmacionManualYComandosQueNoSeUsan() {
        var user = connect();
        assertThatThrownBy(() -> send(frame(StompCommand.SUBSCRIBE, Map.of("id", "sub-0",
                "destination", "/topic/user." + ANA, "ack", "client"), user))).isInstanceOf(MessageDeliveryException.class);
        for (StompCommand command : new StompCommand[] {StompCommand.SEND, StompCommand.ACK, StompCommand.NACK,
                StompCommand.BEGIN, StompCommand.COMMIT, StompCommand.ABORT}) {
            assertThatThrownBy(() -> send(frame(command, Map.of("id", "1"), user))).as(command.name())
                    .isInstanceOf(MessageDeliveryException.class);
        }
    }

    @Test
    void noPermiteSuscribirseATemasAjenosNiSinConectar() {
        var user = connect();
        assertThatThrownBy(() -> send(frame(StompCommand.SUBSCRIBE, Map.of("id", "s",
                "destination", "/topic/user." + UUID.randomUUID()), user))).isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> send(frame(StompCommand.SUBSCRIBE, Map.of("id", "s",
                "destination", "/topic/user." + ANA), null))).isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void conElTokenVencidoNoSuscribePeroSiPermiteSalir() {
        var user = connect();
        now = tokenExpiresAt;
        assertThat(StompAuthInterceptor.isExpired(session, now)).isTrue();
        assertThatThrownBy(() -> send(frame(StompCommand.SUBSCRIBE, Map.of("id", "s",
                "destination", "/topic/user." + ANA), user))).isInstanceOf(MessageDeliveryException.class);
        send(frame(StompCommand.UNSUBSCRIBE, Map.of("id", "s"), user));
        send(frame(StompCommand.DISCONNECT, Map.of("receipt", "bye"), user));
    }

    @Test
    void sinTokenRegistradoLaSesionNoVence() {
        assertThat(StompAuthInterceptor.isExpired(Map.of(), NOW)).isFalse();
        assertThat(StompAuthInterceptor.isExpired(null, NOW)).isFalse();
    }

    // ---- utilidades ----

    private StompAuthInterceptor.UserPrincipal connect() {
        return (StompAuthInterceptor.UserPrincipal) send(frame(StompCommand.CONNECT,
                Map.of("Authorization", "Bearer valido"))).getUser();
    }

    private Message<byte[]> frame(StompCommand command, Map<String, String> headers) {
        return frame(command, headers, null);
    }

    private Message<byte[]> frame(StompCommand command, Map<String, String> headers,
                                  StompAuthInterceptor.UserPrincipal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        // Como el decodificador de STOMP: «destination» también llena el destino de Spring.
        headers.forEach((name, value) -> {
            if (name.equals("destination")) {
                accessor.setDestination(value);
            } else {
                accessor.addNativeHeader(name, value);
            }
        });
        accessor.setSessionId("s-1");
        accessor.setSessionAttributes(session);
        accessor.setUser(user);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private StompHeaderAccessor send(Message<byte[]> message) {
        interceptor.preSend(message, null);
        return StompHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    }
}
