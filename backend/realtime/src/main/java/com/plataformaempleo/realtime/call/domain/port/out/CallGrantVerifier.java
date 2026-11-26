package com.plataformaempleo.realtime.call.domain.port.out;

import com.plataformaempleo.realtime.call.domain.model.CallGrant;

/** Comprueba la firma, el emisor, la audiencia y la vigencia del token de sala emitido por la API. */
public interface CallGrantVerifier {
    /** @throws IllegalArgumentException si el token no es válido o venció */
    CallGrant verify(String roomToken);
}
