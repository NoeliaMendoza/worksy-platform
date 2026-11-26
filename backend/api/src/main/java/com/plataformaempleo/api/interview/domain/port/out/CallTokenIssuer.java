package com.plataformaempleo.api.interview.domain.port.out;

import com.plataformaempleo.api.interview.domain.model.CallRoom;

/** Emite el token de sala que el servicio de tiempo real exige para reenviar señales de la llamada. */
public interface CallTokenIssuer {
    String issue(CallRoom.Grant grant);
}
