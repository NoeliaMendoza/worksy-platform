package com.plataformaempleo.api.identity.infrastructure.security;

import com.plataformaempleo.api.identity.domain.port.out.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** Hash de contraseñas con BCrypt (coste 12). VALOR SUPUESTO: confirmar con el diseño técnico. */
@Component
class BCryptPasswordHasher implements PasswordHasher {

    static final int STRENGTH = 12;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(STRENGTH);

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String hash) {
        return encoder.matches(rawPassword, hash);
    }
}
