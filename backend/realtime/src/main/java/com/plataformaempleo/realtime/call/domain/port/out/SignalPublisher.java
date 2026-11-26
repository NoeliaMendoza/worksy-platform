package com.plataformaempleo.realtime.call.domain.port.out;

import com.plataformaempleo.realtime.call.domain.model.CallGrant;
import com.plataformaempleo.realtime.call.domain.model.CallSignal;

/** Entrega una señal en el tema personal de su destinatario (en cualquier nodo, vía el broker). */
public interface SignalPublisher {
    void publish(CallGrant grant, CallSignal signal);
}
