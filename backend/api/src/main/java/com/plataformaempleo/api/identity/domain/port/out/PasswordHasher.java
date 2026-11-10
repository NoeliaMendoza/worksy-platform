package com.plataformaempleo.api.identity.domain.port.out;

/** Puerto de salida: calcula y verifica hashes de contraseña (el algoritmo lo decide el adaptador). */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hash);
}
