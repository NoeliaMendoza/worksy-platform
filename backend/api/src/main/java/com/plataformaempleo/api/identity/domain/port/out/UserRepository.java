package com.plataformaempleo.api.identity.domain.port.out;

import com.plataformaempleo.api.identity.domain.model.User;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida: lo implementa un adaptador de persistencia. */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    /** Busca solo usuarios activos (no borrados lógicamente). */
    Optional<User> findActiveByEmail(String email);
}
