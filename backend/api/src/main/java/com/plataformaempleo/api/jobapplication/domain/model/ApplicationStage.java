package com.plataformaempleo.api.jobapplication.domain.model;

/** Etapa comunicada por la empresa; independiente del ranking automático. */
public enum ApplicationStage {
    RECEIVED, REVIEW, INTERVIEW, OFFER, HIRED, REJECTED;

    public boolean canMoveTo(ApplicationStage next) {
        return switch (this) {
            case RECEIVED -> next == REVIEW || next == REJECTED;
            case REVIEW -> next == INTERVIEW || next == REJECTED;
            case INTERVIEW -> next == OFFER || next == REJECTED;
            case OFFER -> next == HIRED || next == REJECTED;
            case HIRED, REJECTED -> false;
        };
    }
}
