package com.plataformaempleo.api.company.domain.port.out;

/** Puerto de salida: envía la invitación para unirse al equipo de una empresa. */
public interface TeamInvitationMailer {

    /** Envía sin bloquear la respuesta; un fallo del correo no se propaga a quien invita. */
    void sendInvitation(String email, String companyName, String activationLink, long validHours);
}
