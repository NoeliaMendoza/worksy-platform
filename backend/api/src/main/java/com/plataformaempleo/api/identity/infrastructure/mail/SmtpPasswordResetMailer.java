package com.plataformaempleo.api.identity.infrastructure.mail;

import com.plataformaempleo.api.identity.domain.port.out.PasswordResetMailer;
import jakarta.mail.internet.MimeMessage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Envía el enlace de recuperación por SMTP (Gmail u otro). Si el correo no está configurado
 * (sin {@code MAIL_HOST}), lo registra en el log sin incluir el enlace, que es un secreto.
 */
@Component
class SmtpPasswordResetMailer implements PasswordResetMailer, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SmtpPasswordResetMailer.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final MailProperties mailProps;
    private final String from;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    SmtpPasswordResetMailer(ObjectProvider<JavaMailSender> sender, MailProperties mailProps,
                            @Value("${worksy.mail.from:}") String from) {
        this.sender = sender;
        this.mailProps = mailProps;
        this.from = from;
    }

    boolean isConfigured() {
        return mailProps.getHost() != null && !mailProps.getHost().isBlank() && sender.getIfAvailable() != null;
    }

    @Override
    public void sendResetLink(String email, String resetLink, long validMinutes) {
        if (!isConfigured()) {
            log.warn("Correo no configurado (MAIL_HOST vacío): no se envió el enlace de recuperación.");
            return;
        }
        executor.execute(() -> {
            try {
                JavaMailSender mail = sender.getObject();
                MimeMessage message = mail.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(from.isBlank() ? mailProps.getUsername() : from);
                helper.setTo(email);
                helper.setSubject("Restablece tu contraseña de Worksy");
                helper.setText(plainText(resetLink, validMinutes), html(resetLink, validMinutes));
                mail.send(message);
            } catch (Exception e) {
                // Sin el correo ni el enlace en el log: son datos personales y un secreto.
                log.error("No se pudo enviar el correo de recuperación: {}", e.getClass().getSimpleName());
            }
        });
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

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
