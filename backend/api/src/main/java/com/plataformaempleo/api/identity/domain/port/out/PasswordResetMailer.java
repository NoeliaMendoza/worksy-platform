package com.plataformaempleo.api.identity.domain.port.out;

/** Puerto de salida: envía el enlace de recuperación. El adaptador decide el canal (SMTP, etc.). */
public interface PasswordResetMailer {

    /**
     * Envía el enlace sin bloquear la respuesta: el tiempo de respuesta no debe revelar
     * si la cuenta existe, y un fallo del correo no debe filtrarse al usuario.
     */
    void sendResetLink(String email, String resetLink, long validMinutes);
}
