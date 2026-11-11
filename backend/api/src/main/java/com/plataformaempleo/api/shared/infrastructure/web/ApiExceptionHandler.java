package com.plataformaempleo.api.shared.infrastructure.web;

import com.plataformaempleo.common.domain.DomainException;
import java.net.URI;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce errores a ProblemDetail (RFC 7807). Cada problema lleva un {@code code} estable
 * que el frontend puede usar; el texto es para personas y no revela detalles internos.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> domain(DomainException e) {
        return toResponse(e);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> integrity(DataIntegrityViolationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "La operación entra en conflicto con datos existentes");
        problem.setTitle("Conflicto");
        problem.setType(URI.create("urn:worksy:problem:conflict"));
        problem.setProperty("code", "data.conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    /** Disponible para manejadores locales que necesitan añadir cabeceras (p. ej., borrar una cookie). */
    public static ResponseEntity<ProblemDetail> toResponse(DomainException e, HttpHeaders extraHeaders) {
        HttpStatus status = statusFor(e.code());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("urn:worksy:problem:" + e.code()));
        problem.setProperty("code", e.code());
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (status == HttpStatus.UNAUTHORIZED) {
            builder.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        if (extraHeaders != null) {
            builder.headers(extraHeaders);
        }
        return builder.body(problem);
    }

    static ResponseEntity<ProblemDetail> toResponse(DomainException e) {
        return toResponse(e, null);
    }

    /** auth.* → 401; *.taken → 409; resto de reglas de negocio → 400. */
    static HttpStatus statusFor(String code) {
        if (code == null) {
            return HttpStatus.BAD_REQUEST;
        }
        if (code.startsWith("auth.")) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (code.endsWith(".taken")) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
