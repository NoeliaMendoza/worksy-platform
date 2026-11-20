package com.plataformaempleo.api.shared.infrastructure.messaging;

import com.plataformaempleo.common.events.EventTypes;
import com.plataformaempleo.common.events.Messaging;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Topología de RabbitMQ (sección 1 del diseño: colas durables, reintentos y cola de mensajes fallidos).
 * La declaran la API y el worker con los mismos nombres ({@link Messaging}); declarar dos veces es inocuo.
 * Si la API declara la cola, un evento publicado antes de que arranque el worker no se pierde.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
class MessagingConfig {

    @Bean
    Declarables screeningTopology() {
        TopicExchange events = new TopicExchange(Messaging.EVENTS_EXCHANGE, true, false);
        DirectExchange deadLetters = new DirectExchange(Messaging.DEAD_LETTER_EXCHANGE, true, false);
        // Cola quorum: replicada cuando RabbitMQ funcione en clúster (sección 14.3 del diseño).
        Queue requested = QueueBuilder.durable(Messaging.SCREENING_REQUESTED_QUEUE)
                .quorum()
                .deadLetterExchange(Messaging.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(Messaging.SCREENING_REQUESTED_DLQ)
                .build();
        Queue failed = QueueBuilder.durable(Messaging.SCREENING_REQUESTED_DLQ).quorum().build();
        Binding requestedBinding = BindingBuilder.bind(requested).to(events).with(EventTypes.SCREENING_REQUESTED);
        Binding failedBinding = BindingBuilder.bind(failed).to(deadLetters).with(Messaging.SCREENING_REQUESTED_DLQ);
        return new Declarables(events, deadLetters, requested, failed, requestedBinding, failedBinding);
    }

    /**
     * Cola de avisos del servicio de tiempo real. La declara también la API para que un mensaje enviado antes
     * de que arranque el realtime no se pierda. Debe coincidir EXACTAMENTE con la declaración del realtime.
     */
    @Bean
    Declarables realtimeTopology() {
        TopicExchange events = new TopicExchange(Messaging.EVENTS_EXCHANGE, true, false);
        DirectExchange deadLetters = new DirectExchange(Messaging.DEAD_LETTER_EXCHANGE, true, false);
        Queue notifications = QueueBuilder.durable(Messaging.REALTIME_NOTIFICATIONS_QUEUE)
                .quorum()
                .ttl(Messaging.REALTIME_NOTIFICATIONS_TTL_MS)
                .deadLetterExchange(Messaging.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(Messaging.REALTIME_NOTIFICATIONS_DLQ)
                .build();
        Queue failed = QueueBuilder.durable(Messaging.REALTIME_NOTIFICATIONS_DLQ).quorum().build();
        return new Declarables(notifications, failed,
                BindingBuilder.bind(notifications).to(events).with(EventTypes.MESSAGE_SENT),
                BindingBuilder.bind(notifications).to(events).with(EventTypes.APPLICATION_STAGE_CHANGED),
                BindingBuilder.bind(notifications).to(events).with(EventTypes.INTERVIEW_CHANGED),
                BindingBuilder.bind(failed).to(deadLetters).with(Messaging.REALTIME_NOTIFICATIONS_DLQ));
    }
}
