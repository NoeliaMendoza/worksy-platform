package com.plataformaempleo.realtime.call.infrastructure;

import com.plataformaempleo.realtime.call.application.CallSignalService;
import com.plataformaempleo.realtime.notification.infrastructure.RealtimeProperties;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Señalización de la videollamada: conecta el caso de uso con el verificador de tokens de sala y el adaptador STOMP. */
@Configuration
class CallConfig {

    @Bean
    CallSignalService callSignalService(RealtimeProperties props, StompCallSignaling signaling, Clock clock) {
        CallSignalService service = new CallSignalService(new JwtCallGrantVerifier(props.jwtSecret()), signaling, clock);
        signaling.use(service);
        return service;
    }
}
