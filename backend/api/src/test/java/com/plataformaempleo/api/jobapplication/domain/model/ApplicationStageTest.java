package com.plataformaempleo.api.jobapplication.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApplicationStageTest {
    @Test
    void soloPermiteAvanzarUnPasoOCerrarUnProcesoActivo() {
        var path = new ApplicationStage[] {ApplicationStage.RECEIVED, ApplicationStage.REVIEW,
                ApplicationStage.INTERVIEW, ApplicationStage.OFFER, ApplicationStage.HIRED};
        for (int i = 0; i < path.length - 1; i++) {
            assertThat(path[i].canMoveTo(path[i + 1])).isTrue();
            assertThat(path[i].canMoveTo(ApplicationStage.REJECTED)).isTrue();
            assertThat(path[i].canMoveTo(path[i])).isFalse();
        }
        assertThat(ApplicationStage.RECEIVED.canMoveTo(ApplicationStage.HIRED)).isFalse();
        assertThat(ApplicationStage.INTERVIEW.canMoveTo(ApplicationStage.REVIEW)).isFalse();
        for (ApplicationStage next : ApplicationStage.values()) {
            assertThat(ApplicationStage.HIRED.canMoveTo(next)).isFalse();
            assertThat(ApplicationStage.REJECTED.canMoveTo(next)).isFalse();
        }
    }
}
