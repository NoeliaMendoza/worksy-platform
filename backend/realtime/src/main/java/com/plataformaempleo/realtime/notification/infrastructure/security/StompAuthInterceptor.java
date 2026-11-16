package com.plataformaempleo.realtime.notification.infrastructure.security;

import com.plataformaempleo.realtime.notification.domain.model.UserTopic;
import java.security.Principal;
import java.util.Objects;
import java.util.UUID;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Autoriza cada trama STOMP que llega del navegador.
 * <ul>
 *   <li>CONNECT: exige {@code Authorization: Bearer <token>} con un token de acceso válido de la API.</li>
 *   <li>SUBSCRIBE: solo al tema personal de quien se conectó ({@code /topic/user.<su id>}).</li>
 *   <li>SEND: no se acepta; los mensajes se escriben por la API REST, que valida y guarda.</li>
 * </ul>
 * Si una trama se rechaza, Spring responde con una trama ERROR y cierra la conexión.
 */
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final String BEARER = "Bearer ";

    private final JwtDecoder decoder;

    public StompAuthInterceptor(JwtDecoder decoder) {
        this.decoder = Objects.requireNonNull(decoder);
    }

    /** Identidad de la conexión: el id del usuario del token. */
    public record UserPrincipal(UUID userId) implements Principal {
        @Override
        public String getName() {
            return userId.toString();
        }
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor stomp = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (stomp == null || stomp.getCommand() == null) {
            return message;
        }
        switch (stomp.getCommand()) {
            case CONNECT, STOMP -> stomp.setUser(authenticate(stomp.getFirstNativeHeader("Authorization")));
            case SUBSCRIBE -> {
                UUID userId = userOf(stomp);
                if (!UserTopic.isOwnedBy(stomp.getDestination(), userId)) {
                    throw new MessageDeliveryException("Solo puedes suscribirte a tus propios avisos");
                }
            }
            case SEND -> throw new MessageDeliveryException("Los mensajes se envían por la API, no por el WebSocket");
            default -> {
                // DISCONNECT, UNSUBSCRIBE, ACK...: no necesitan más permisos.
            }
        }
        return message;
    }

    private UserPrincipal authenticate(String header) {
        if (header == null || !header.startsWith(BEARER)) {
            throw new MessageDeliveryException("Falta el token de acceso");
        }
        try {
            return new UserPrincipal(UUID.fromString(decoder.decode(header.substring(BEARER.length())).getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            throw new MessageDeliveryException("El token de acceso no es válido o venció");
        }
    }

    private static UUID userOf(StompHeaderAccessor stomp) {
        if (stomp.getUser() instanceof UserPrincipal user) {
            return user.userId();
        }
        throw new MessageDeliveryException("Conéctate con un token de acceso antes de suscribirte");
    }

}
