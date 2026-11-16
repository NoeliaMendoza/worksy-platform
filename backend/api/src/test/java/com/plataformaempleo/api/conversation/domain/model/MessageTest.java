package com.plataformaempleo.api.conversation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plataformaempleo.common.domain.DomainException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageTest {

    private final Instant now = Instant.parse("2026-11-15T12:00:00Z");
    private final UUID application = UUID.randomUUID();
    private final UUID sender = UUID.randomUUID();

    @Test
    void quitaLosEspaciosDeLosExtremos() {
        Message m = Message.write(application, sender, false, "  Hola, ¿podemos hablar el lunes?\n ", now);
        assertThat(m.body()).isEqualTo("Hola, ¿podemos hablar el lunes?");
        assertThat(m.fromCandidate()).isFalse();
        assertThat(m.sentAt()).isEqualTo(now);
    }

    @Test
    void rechazaMensajesVacios() {
        assertThatThrownBy(() -> Message.write(application, sender, true, " \n\t ", now))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo("message.body.empty");
        assertThatThrownBy(() -> Message.write(application, sender, true, null, now))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void cuentaCaracteresYNoUnidadesUtf16() {
        // 2000 emojis son 4000 unidades UTF-16 pero 2000 caracteres: se aceptan.
        String emojis = "🙂".repeat(Message.MAX_LENGTH);
        assertThat(Message.write(application, sender, true, emojis, now).body()).isEqualTo(emojis);

        assertThatThrownBy(() -> Message.write(application, sender, true, "a".repeat(Message.MAX_LENGTH + 1), now))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo("message.body.too-long");
    }
}
