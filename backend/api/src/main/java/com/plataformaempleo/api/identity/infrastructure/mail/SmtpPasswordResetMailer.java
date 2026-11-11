package com.plataformaempleo.api.identity.infrastructure.mail;

import com.plataformaempleo.api.identity.domain.port.out.PasswordResetMailer;
import com.plataformaempleo.api.shared.infrastructure.mail.AsyncMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/** Correo con el enlace para restablecer la contraseña. El envío lo hace {@link AsyncMailSender}. */
@Component
class SmtpPasswordResetMailer implements PasswordResetMailer {

    private final AsyncMailSender mail;

    SmtpPasswordResetMailer(AsyncMailSender mail) {
        this.mail = mail;
    }

    @Override
    public void sendResetLink(String email, String resetLink, long validMinutes) {
        mail.send(email, "Restablece tu contraseña de Worksy", plainText(resetLink, validMinutes),
                html(resetLink, validMinutes), "recuperación de contraseña");
    }

    static String plainText(String link, long minutes) {
        return """
                Hola:

                Recibimos una solicitud para restablecer la contraseña de tu cuenta de Worksy.
                Abre este enlace para elegir una nueva (vale %d minutos y solo funciona una vez):

                %s

                Si no lo pediste, ignora este correo: tu contraseña no cambiará.

                Worksy
                """.formatted(minutes, link);
    }

    static String html(String link, long minutes) {
        String safe = HtmlUtils.htmlEscape(link);
        return """
                <div style="font-family:Arial,sans-serif;color:#0F172A;max-width:520px;line-height:1.6">
                  <p style="font-size:20px;color:#0B1F4B;margin:0 0 16px"><strong>Worksy</strong></p>
                  <p>Recibimos una solicitud para restablecer la contraseña de tu cuenta.</p>
                  <p><a href="%s" style="display:inline-block;background:#0B1F4B;color:#ffffff;padding:12px 24px;
                     border-radius:999px;text-decoration:none;font-weight:bold">Elegir una contraseña nueva</a></p>
                  <p style="color:#5B6B86;font-size:14px">El enlace vale %d minutos y solo funciona una vez.
                     Si no lo pediste, ignora este correo: tu contraseña no cambiará.</p>
                </div>
                """.formatted(safe, minutes);
    }
}
