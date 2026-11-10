package com.plataformaempleo.api.identity.domain.port.out;

import com.plataformaempleo.api.identity.domain.model.ExternalIdentity;
import java.util.Optional;

/** Puerto de salida para los vínculos con cuentas externas. */
public interface ExternalIdentityRepository {

    Optional<ExternalIdentity> findByProviderAndSubject(String provider, String subject);

    ExternalIdentity save(ExternalIdentity identity);
}
