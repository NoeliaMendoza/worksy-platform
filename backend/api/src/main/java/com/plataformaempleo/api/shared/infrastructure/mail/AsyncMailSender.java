package com.plataformaempleo.api.shared.infrastructure.mail;

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

/**
 * Envío de correo por SMTP sin bloquear la petición. Si el correo no está configurado
 * (sin {@code MAIL_HOST}) no envía nada y lo avisa en el log. Nunca registra destinatarios
 * ni contenidos: suelen llevar datos personales o enlaces secretos.
 */
@Component
public class AsyncMailSender implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(AsyncMailSender.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final MailProperties mailProps;
    private final String from;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    AsyncMailSender(ObjectProvider<JavaMailSender> sender, MailProperties mailProps,
                    @Value("${worksy.mail.from:}") String from) {
        this.sender = sender;
        this.mailProps = mailProps;
        this.from = from;
    }

    public boolean isConfigured() {
        return mailProps.getHost() != null && !mailProps.getHost().isBlank() && sender.getIfAvailable() != null;
    }

    /** @param purpose texto corto para el log ("recuperación", "invitación"), sin datos personales. */
    public void send(String to, String subject, String plainText, String html, String purpose) {
        if (!isConfigured()) {
            log.warn("Correo no configurado (MAIL_HOST vacío): no se envió el correo de {}.", purpose);
            return;
        }
        executor.execute(() -> {
            try {
                JavaMailSender mail = sender.getObject();
                MimeMessage message = mail.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(from.isBlank() ? mailProps.getUsername() : from);
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(plainText, html);
                mail.send(message);
            } catch (Exception e) {
                log.error("No se pudo enviar el correo de {}: {}", purpose, e.getClass().getSimpleName());
            }
        });
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
