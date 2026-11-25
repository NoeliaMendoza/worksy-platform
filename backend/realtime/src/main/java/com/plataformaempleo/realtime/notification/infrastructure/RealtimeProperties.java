package com.plataformaempleo.realtime.notification.infrastructure;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del servicio de tiempo real ({@code worksy.realtime.*}).
 *
 * @param jwtSecret      mismo secreto HS256 que la API; mínimo 32 bytes
 * @param issuer         emisor esperado en los tokens
 * @param allowedOrigins orígenes del navegador que pueden abrir el WebSocket
 * @param relay          conexión STOMP con RabbitMQ
 */
@ConfigurationProperties("worksy.realtime")
public record RealtimeProperties(String jwtSecret, String issuer, List<String> allowedOrigins, Relay relay) {

    public RealtimeProperties {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Falta JWT_SECRET (mínimo 32 caracteres), el mismo que usa la API");
        }
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }

    /** @param virtualHost virtual host de RabbitMQ para todas las conexiones del relay; por defecto {@code /} */
    public record Relay(String host, int port, String login, String passcode, String virtualHost) {
        public Relay {
            virtualHost = virtualHost == null || virtualHost.isBlank() ? "/" : virtualHost;
        }
    }
}
