package com.plataformaempleo.realtime.call.domain.model;

import java.util.Locale;
import java.util.UUID;

/**
 * Señal WebRTC entre dos personas de una sala. El servicio no interpreta ni guarda {@code data}
 * (descripciones SDP y candidatos ICE): solo comprueba quién la manda, a quién y de qué tipo es.
 */
public record CallSignal(UUID from, UUID to, Kind kind, Object data) {

    /** hello: entro o sigo en la sala; offer/answer/ice: negociación; bye: salgo; state: micrófono o cámara. */
    public enum Kind {
        HELLO, OFFER, ANSWER, ICE, BYE, STATE;

        public static Kind parse(String value) {
            if (value == null) throw new IllegalArgumentException("Falta el tipo de señal");
            return Kind.valueOf(value.toUpperCase(Locale.ROOT));
        }

        public String wire() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
