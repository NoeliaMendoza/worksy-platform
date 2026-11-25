package com.plataformaempleo.realtime.notification.infrastructure.stomp;

import com.plataformaempleo.realtime.notification.infrastructure.security.StompAuthInterceptor;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

/**
 * Conexiones WebSocket abiertas en este nodo. Una conexión ya suscrita no vuelve a mandar el token, así que sin esto
 * seguiría recibiendo avisos después de que venza: {@link #closeExpired()} la cierra y el navegador se reconecta con
 * el token renovado. Las conexiones que nunca envían CONNECT las cierra Spring (tiempo hasta el primer mensaje).
 */
public class ExpiringSessions {

    static final CloseStatus TOKEN_EXPIRED = CloseStatus.POLICY_VIOLATION.withReason("El token de acceso venció");

    private static final Logger log = LoggerFactory.getLogger(ExpiringSessions.class);

    private final Map<String, WebSocketSession> open = new ConcurrentHashMap<>();
    private final Clock clock;

    public ExpiringSessions(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    /** Envuelve el manejador de STOMP para llevar la cuenta de las conexiones abiertas. */
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                open.put(session.getId(), session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                open.remove(session.getId());
                super.afterConnectionClosed(session, status);
            }
        };
    }

    /** Cierra las conexiones cuyo token venció. Devuelve cuántas cerró. */
    public int closeExpired() {
        var now = clock.instant();
        int closed = 0;
        for (WebSocketSession session : open.values()) {
            if (StompAuthInterceptor.isExpired(session.getAttributes(), now)) {
                open.remove(session.getId());
                closed++;
                try {
                    session.close(TOKEN_EXPIRED);
                } catch (IOException | IllegalStateException e) {
                    // Ya estaba cerrándose: no hay nada más que hacer.
                    log.debug("No se pudo cerrar una conexión con el token vencido", e);
                }
            }
        }
        return closed;
    }

    int openCount() {
        return open.size();
    }
}
