package com.plataformaempleo.api.shared.infrastructure;

import com.plataformaempleo.api.shared.application.AuditRecorder;
import com.plataformaempleo.api.shared.application.AuditService;
import com.plataformaempleo.api.shared.domain.port.out.AuditTrail;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AuditConfig {
    @Bean
    AuditRecorder auditRecorder(AuditTrail trail, Clock clock) { return new AuditRecorder(trail, clock); }

    @Bean
    AuditService auditService(AuditTrail trail) { return new AuditService(trail); }
}
