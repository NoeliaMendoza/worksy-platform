package com.plataformaempleo.realtime.notification.infrastructure;

import com.plataformaempleo.common.events.EventTypes;
import com.plataformaempleo.common.events.Messaging;
import com.plataformaempleo.realtime.notification.application.NotificationService;
import com.plataformaempleo.realtime.notification.domain.port.out.NotificationPublisher;
import com.plataformaempleo.realtime.notification.infrastructure.security.StompAuthInterceptor;
import com.plataformaempleo.realtime.notification.infrastructure.stomp.ExpiringSessions;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** Conecta el caso de uso con sus adaptadores y declara la parte de la topología que consume el realtime. */
@Configuration
@EnableConfigurationProperties(RealtimeProperties.class)
class NotificationConfig {

    @Bean
    NotificationService notificationService(NotificationPublisher publisher) {
        return new NotificationService(publisher);
    }

    /** Valida los tokens igual que la API: HS256, mismo secreto, mismo emisor y vigencia. */
    @Bean
    JwtDecoder jwtDecoder(RealtimeProperties props) {
        var key = new SecretKeySpec(props.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.issuer()));
        return decoder;
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    StompAuthInterceptor stompAuthInterceptor(JwtDecoder decoder, Clock clock) {
        return new StompAuthInterceptor(decoder, clock);
    }

    @Bean
    ExpiringSessions expiringSessions(Clock clock) {
        return new ExpiringSessions(clock);
    }

    /** Misma declaración que en la API ({@link Messaging}); declarar dos veces es inocuo. */
    @Bean
    Declarables notificationTopology() {
        TopicExchange events = new TopicExchange(Messaging.EVENTS_EXCHANGE, true, false);
        DirectExchange deadLetters = new DirectExchange(Messaging.DEAD_LETTER_EXCHANGE, true, false);
        Queue notifications = QueueBuilder.durable(Messaging.REALTIME_NOTIFICATIONS_QUEUE)
                .quorum()
                .ttl(Messaging.REALTIME_NOTIFICATIONS_TTL_MS)
                .deadLetterExchange(Messaging.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(Messaging.REALTIME_NOTIFICATIONS_DLQ)
                .build();
        Queue failed = QueueBuilder.durable(Messaging.REALTIME_NOTIFICATIONS_DLQ).quorum().build();
        return new Declarables(events, deadLetters, notifications, failed,
                BindingBuilder.bind(notifications).to(events).with(EventTypes.MESSAGE_SENT),
                BindingBuilder.bind(notifications).to(events).with(EventTypes.APPLICATION_STAGE_CHANGED),
                BindingBuilder.bind(notifications).to(events).with(EventTypes.INTERVIEW_CHANGED),
                BindingBuilder.bind(failed).to(deadLetters).with(Messaging.REALTIME_NOTIFICATIONS_DLQ));
    }
}
