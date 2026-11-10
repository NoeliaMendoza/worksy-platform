package com.plataformaempleo.api.identity.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SmtpPasswordResetMailerTest {

    private static final String LINK = "http://localhost:5173/restablecer?token=abc&x=<b>";

    @Test
    void elTextoPlanoIncluyeElEnlaceYLaVigencia() {
        assertThat(SmtpPasswordResetMailer.plainText(LINK, 30))
                .contains(LINK)
                .contains("30 minutos")
                .contains("ignora este correo");
    }

    @Test
    void elHtmlEscapaElEnlace() {
        String html = SmtpPasswordResetMailer.html(LINK, 30);
        assertThat(html).contains("token=abc&amp;x=&lt;b&gt;").doesNotContain("<b>\"");
        assertThat(html).contains("Elegir una contraseña nueva");
    }
}
