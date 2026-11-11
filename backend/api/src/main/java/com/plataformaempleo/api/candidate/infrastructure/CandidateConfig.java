package com.plataformaempleo.api.candidate.infrastructure;

import com.plataformaempleo.api.candidate.application.ConsentService;
import com.plataformaempleo.api.candidate.application.CvService;
import com.plataformaempleo.api.candidate.domain.port.out.CandidateProfileRepository;
import com.plataformaempleo.api.candidate.domain.port.out.CvDocumentRepository;
import com.plataformaempleo.api.candidate.domain.port.out.CvStorage;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Conecta los casos de uso del candidato con sus adaptadores. */
@Configuration
class CandidateConfig {

    @Bean
    CvService cvService(CvDocumentRepository documents, CvStorage storage, Clock clock) {
        return new CvService(documents, storage, clock);
    }

    @Bean
    ConsentService consentService(CandidateProfileRepository profiles, Clock clock) {
        return new ConsentService(profiles, clock);
    }
}
