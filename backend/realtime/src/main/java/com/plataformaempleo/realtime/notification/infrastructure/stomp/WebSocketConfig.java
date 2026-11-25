package com.plataformaempleo.realtime.notification.infrastructure.stomp;

import com.plataformaempleo.realtime.notification.infrastructure.RealtimeProperties;
import com.plataformaempleo.realtime.notification.infrastructure.security.StompAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * WebSocket con STOMP en {@code /ws} y broker relay hacia el STOMP de RabbitMQ.
 * Decisión de la sección 14.4 del diseño (2026-11-15): broker relay. Cada nodo del realtime es intercambiable:
 * RabbitMQ entrega los avisos a las conexiones de cada persona, estén en el nodo que estén.
 */
@Configuration
@EnableScheduling
@EnableWebSocketMessageBroker
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** VALOR SUPUESTO: latidos cada 10 s entre el realtime y RabbitMQ para detectar conexiones caídas. */
    private static final long HEARTBEAT_MS = 10_000;

    private final RealtimeProperties props;
    private final StompAuthInterceptor auth;
    private final ExpiringSessions sessions;

    WebSocketConfig(RealtimeProperties props, StompAuthInterceptor auth, ExpiringSessions sessions) {
        this.props = props;
        this.auth = auth;
        this.sessions = sessions;
    }

    /** VALOR SUPUESTO: revisar cada 5 s; una conexión vencida recibe avisos como mucho 5 s de más. */
    @Scheduled(fixedDelayString = "${worksy.realtime.session-check-ms:5000}")
    void closeExpiredSessions() {
        sessions.closeExpired();
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(sessions::decorate);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(props.allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        var relay = props.relay();
        registry.enableStompBrokerRelay("/topic")
                .setRelayHost(relay.host())
                .setRelayPort(relay.port())
                // Fijo: si no, la cabecera «host» del CONNECT del navegador elegiría el virtual host de RabbitMQ.
                .setVirtualHost(relay.virtualHost())
                .setClientLogin(relay.login())
                .setClientPasscode(relay.passcode())
                .setSystemLogin(relay.login())
                .setSystemPasscode(relay.passcode())
                .setSystemHeartbeatSendInterval(HEARTBEAT_MS)
                .setSystemHeartbeatReceiveInterval(HEARTBEAT_MS);
        // El navegador no envía tramas SEND (el interceptor las rechaza); el prefijo queda reservado.
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(auth);
    }
}
