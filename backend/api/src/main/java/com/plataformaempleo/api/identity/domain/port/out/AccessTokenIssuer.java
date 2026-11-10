package com.plataformaempleo.api.identity.domain.port.out;

import com.plataformaempleo.api.identity.domain.model.AccessToken;
import com.plataformaempleo.api.identity.domain.model.User;

/** Puerto de salida: emite el token de acceso de un usuario (formato y firma los decide el adaptador). */
public interface AccessTokenIssuer {

    AccessToken issue(User user);
}
