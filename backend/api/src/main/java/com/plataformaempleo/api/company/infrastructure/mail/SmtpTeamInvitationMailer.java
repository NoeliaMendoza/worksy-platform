package com.plataformaempleo.api.company.infrastructure.mail;

import com.plataformaempleo.api.company.domain.port.out.TeamInvitationMailer;
import com.plataformaempleo.api.shared.infrastructure.mail.AsyncMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/** Correo de invitación al equipo de una empresa. El envío lo hace {@link AsyncMailSender}. */
@Component
class SmtpTeamInvitationMailer implements TeamInvitationMailer {

    private final AsyncMailSender mail;

    SmtpTeamInvitationMailer(AsyncMailSender mail) {
        this.mail = mail;
    }

    @Override
    public void sendInvitation(String email, String companyName, String activationLink, long validHours) {
        mail.send(email, "Te invitaron a " + companyName + " en Worksy",
                plainText(companyName, activationLink, validHours),
                html(companyName, activationLink, validHours), "invitación al equipo");
    }

    static String plainText(String company, String link, long hours) {
        return """
                Hola:

                %s te invitó a unirte a su equipo de selección en Worksy.
                Abre este enlace para activar tu cuenta y elegir tu contraseña (vale %d horas y solo funciona una vez):

                %s

                Si no esperabas esta invitación, ignora este correo.

                Worksy
                """.formatted(company, hours, link);
    }

    static String html(String company, String link, long hours) {
        return """
                <div style="font-family:Arial,sans-serif;color:#0F172A;max-width:520px;line-height:1.6">
                  <p style="font-size:20px;color:#0B1F4B;margin:0 0 16px"><strong>Worksy</strong></p>
                  <p><strong>%s</strong> te invitó a unirte a su equipo de selección.</p>
                  <p><a href="%s" style="display:inline-block;background:#0B1F4B;color:#ffffff;padding:12px 24px;
                     border-radius:999px;text-decoration:none;font-weight:bold">Activar mi cuenta</a></p>
                  <p style="color:#5B6B86;font-size:14px">El enlace vale %d horas y solo funciona una vez.
                     Si no esperabas esta invitación, ignora este correo.</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(company), HtmlUtils.htmlEscape(link), hours);
    }
}
