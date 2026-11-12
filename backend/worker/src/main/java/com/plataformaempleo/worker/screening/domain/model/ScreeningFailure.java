package com.plataformaempleo.worker.screening.domain.model;

/**
 * Fallo al procesar una preselección.
 * <ul>
 *   <li>{@code retryable = true}: problema pasajero (inferencia caída, almacenamiento no responde) que
 *       sigue fallando tras los reintentos. El mensaje va a la cola de mensajes fallidos.</li>
 *   <li>{@code retryable = false}: repetir no ayudaría (PDF sin texto, CV inexistente).</li>
 * </ul>
 * En ambos casos la solicitud queda FAILED y una persona revisa el CV a mano.
 */
public class ScreeningFailure extends RuntimeException {

    private final boolean retryable;

    public ScreeningFailure(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public static ScreeningFailure permanent(String message) {
        return new ScreeningFailure(message, false, null);
    }

    public static ScreeningFailure transientFailure(String message, Throwable cause) {
        return new ScreeningFailure(message, true, cause);
    }

    public boolean retryable() {
        return retryable;
    }
}
