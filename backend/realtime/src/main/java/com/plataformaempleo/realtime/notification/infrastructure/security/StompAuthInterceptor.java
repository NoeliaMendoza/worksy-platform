package com.plataformaempleo.realtime.notification.infrastructure.security;

import com.plataformaempleo.realtime.notification.domain.model.UserTopic;
import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Autoriza cada trama STOMP que llega del navegador.
 * <ul>
 *   <li>CONNECT: exige {@code Authorization: Bearer <token>} con un token de acceso vigente de la API y guarda
 *       cuándo vence en la sesión ({@link #EXPIRES_AT}).</li>
 *   <li>SUBSCRIBE: solo al tema personal de quien se conectó ({@code /topic/user.<su id>}), con confirmación
 *       automática y mientras el token siga vigente.</li>
 *   <li>SEND, ACK, NACK y transacciones: no se aceptan; los mensajes se escriben por la API REST.</li>
 * </ul>
 * Cada comando admite solo sus cabeceras estándar. El broker relay reenvía las cabeceras a RabbitMQ, que interpreta
 * algunas propias ({@code x-queue-name}, {@code durable}, {@code auto-delete}…) para elegir o crear colas: sin esta
 * lista blanca, alguien podría engancharse a una cola interna y quedarse con avisos ajenos.
 * Si una trama se rechaza, Spring responde con una trama ERROR y cierra la conexión.
 */
public class StompAuthInterceptor implements ChannelInterceptor {

    /** Atributo de la sesión WebSocket con el {@link Instant} en que vence el token con el que se conectó. */
    public static final String EXPIRES_AT = "worksy.accessTokenExpiresAt";

    private static final String BEARER = "Bearer ";

    private static final Set<String> CONNECT_HEADERS =
            Set.of("accept-version", "heart-beat", "host", "login", "passcode", "Authorization");

    /** Cabeceras admitidas por comando (STOMP 1.2). Un comando que no está aquí se rechaza. */
    private static final Map<StompCommand, Set<String>> ALLOWED_HEADERS = Map.of(
            StompCommand.CONNECT, CONNECT_HEADERS,
            StompCommand.STOMP, CONNECT_HEADERS,
            StompCommand.SUBSCRIBE, Set.of("id", "destination", "ack", "receipt"),
            StompCommand.UNSUBSCRIBE, Set.of("id", "receipt"),
            StompCommand.DISCONNECT, Set.of("receipt"));

    private final JwtDecoder decoder;
    private final Clock clock;

    public StompAuthInterceptor(JwtDecoder decoder, Clock clock) {
        this.decoder = Objects.requireNonNull(decoder);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Identidad de la conexión: el id del usuario del token. */
    public record UserPrincipal(UUID userId) implements Principal {
        @Override
        public String getName() {
            return userId.toString();
        }
    }

    /** Si la sesión se abrió con un token que ya venció en {@code now}. Sin token registrado, no vence. */
    public static boolean isExpired(Map<String, Object> sessionAttributes, Instant now) {
        return sessionAttributes != null
                && sessionAttributes.get(EXPIRES_AT) instanceof Instant expiresAt
                && !expiresAt.isAfter(now);
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor stomp = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (stomp == null || stomp.getCommand() == null) {
            return message; // latidos
        }
        StompCommand command = stomp.getCommand();
        if (command == StompCommand.SEND) {
            throw new MessageDeliveryException("Los mensajes se envían por la API, no por el WebSocket");
        }
        Set<String> allowed = ALLOWED_HEADERS.get(command);
        if (allowed == null) {
            throw new MessageDeliveryException("Esta conexión solo admite suscribirse a tus avisos");
        }
        rejectUnknownHeaders(stomp, allowed);
        switch (command) {
            case CONNECT, STOMP -> stomp.setUser(authenticate(stomp));
            case SUBSCRIBE -> {
                UUID userId = userOf(stomp);
                requireLiveToken(stomp);
                if (!UserTopic.isOwnedBy(stomp.getDestination(), userId)) {
                    throw new MessageDeliveryException("Solo puedes suscribirte a tus propios avisos");
                }
                String ack = stomp.getFirstNativeHeader("ack");
                if (ack != null && !ack.equals("auto")) {
                    throw new MessageDeliveryException("Los avisos se confirman de forma automática");
                }
            }
            default -> {
                // UNSUBSCRIBE y DISCONNECT: siempre se permiten, también con el token vencido.
            }
        }
        return message;
    }

    private static void rejectUnknownHeaders(StompHeaderAccessor stomp, Set<String> allowed) {
        Map<String, ?> nativeHeaders = stomp.toNativeHeaderMap();
        for (String name : nativeHeaders.keySet()) {
            if (!allowed.contains(name)) {
                // No se repite el nombre recibido en la respuesta: puede ser texto arbitrario del cliente.
                throw new MessageDeliveryException("La trama trae cabeceras que este servicio no admite");
            }
        }
    }

    private UserPrincipal authenticate(StompHeaderAccessor stomp) {
        String header = stomp.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            throw new MessageDeliveryException("Falta el token de acceso");
        }
        Jwt jwt;
        UUID userId;
        try {
            jwt = decoder.decode(header.substring(BEARER.length()));
            userId = UUID.fromString(jwt.getSubject());
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            throw new MessageDeliveryException("El token de acceso no es válido o venció");
        }
        // El validador tolera 60 s de desfase de relojes; aquí no: un token vencido no abre conexiones.
        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt == null || !expiresAt.isAfter(clock.instant())) {
            throw new MessageDeliveryException("El token de acceso no es válido o venció");
        }
        Map<String, Object> attributes = stomp.getSessionAttributes();
        if (attributes != null) {
            attributes.put(EXPIRES_AT, expiresAt);
        }
        return new UserPrincipal(userId);
    }

    private void requireLiveToken(StompHeaderAccessor stomp) {
        if (isExpired(stomp.getSessionAttributes(), clock.instant())) {
            throw new MessageDeliveryException("El token de acceso venció; vuelve a conectarte");
        }
    }

    private static UUID userOf(StompHeaderAccessor stomp) {
        if (stomp.getUser() instanceof UserPrincipal user) {
            return user.userId();
        }
        throw new MessageDeliveryException("Conéctate con un token de acceso antes de suscribirte");
    }

}
