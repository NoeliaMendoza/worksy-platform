package com.plataformaempleo.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DomainExceptionTest {

    @Test
    void conservaCodigoYMensaje() {
        var ex = new DomainException("user.email.invalid", "El correo no es válido");

        assertThat(ex.code()).isEqualTo("user.email.invalid");
        assertThat(ex.getMessage()).isEqualTo("El correo no es válido");
    }
}
