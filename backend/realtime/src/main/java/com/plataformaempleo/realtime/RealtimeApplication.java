package com.plataformaempleo.realtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Servicio en tiempo real. La configuración STOMP (broker relay contra RabbitMQ o difusión
 * entre nodos) se decide con la prueba temprana de la fase F3 (sección 14.4 del diseño).
 */
@SpringBootApplication
public class RealtimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(RealtimeApplication.class, args);
    }
}
