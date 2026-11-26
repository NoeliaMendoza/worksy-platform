package com.plataformaempleo.realtime.call.infrastructure;

import com.plataformaempleo.realtime.call.application.CallSignalService;
import com.plataformaempleo.realtime.call.domain.model.CallGrant;
import com.plataformaempleo.realtime.call.domain.model.CallSignal;
import com.plataformaempleo.realtime.call.domain.port.out.SignalPublisher;
import com.plataformaempleo.realtime.notification.domain.model.UserTopic;
import com.plataformaempleo.realtime.notification.infrastructure.security.StompAuthInterceptor;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Adaptador STOMP de la señalización: recibe en {@code /app/call.signal} y entrega en {@code /topic/user.<destinatario>}
 * con el tipo {@code call.signal}. Si la señal se rechaza, solo avisa a quien la mandó ({@code call.error}) y no la reenvía.
 * Nunca registra el contenido de las señales (SDP o candidatos ICE pueden contener direcciones IP).
 */
@Controller
class StompCallSignaling implements SignalPublisher {
    static final String GRANT = "worksy.callGrant";
    private static final Logger log = LoggerFactory.getLogger(StompCallSignaling.class);

    private final SimpMessagingTemplate stomp;
    private CallSignalService service;

    StompCallSignaling(SimpMessagingTemplate stomp) {
        this.stomp = stomp;
    }

    void use(CallSignalService service) {
        this.service = service;
    }

    /** Lo que manda el navegador: token de sala, destinatario, tipo y datos de la señal. */
    record SignalBody(String token, UUID to, String kind, Object data) {
        @Override public String toString() { return "SignalBody[" + kind + "]"; }
    }

    @MessageMapping("call.signal")
    void signal(@Payload SignalBody body, Principal principal, SimpMessageHeaderAccessor headers) {
        UUID sender = principal instanceof StompAuthInterceptor.UserPrincipal user ? user.userId() : null;
        try {
            CallGrant grant = service.relay(sender, body.token(), body.to(), body.kind(), body.data());
            Map<String, Object> session = headers.getSessionAttributes();
            if (session != null) session.put(GRANT, grant);
        } catch (CallSignalService.SignalRejected e) {
            log.debug("Señal de videollamada rechazada: {}", e.getMessage());
            if (sender != null) stomp.convertAndSend(UserTopic.of(sender), Map.of("type", "call.error", "message", e.getMessage()));
        }
    }

    @EventListener
    void disconnected(SessionDisconnectEvent event) {
        Map<String, Object> session = StompHeaderAccessor.wrap(event.getMessage()).getSessionAttributes();
        if (session != null && session.get(GRANT) instanceof CallGrant grant) service.disconnected(grant);
    }

    @Override
    public void publish(CallGrant grant, CallSignal signal) {
        Map<String, Object> notice = new LinkedHashMap<>();
        notice.put("type", "call.signal");
        notice.put("interviewId", grant.interviewId().toString());
        notice.put("sessionId", grant.sessionId().toString());
        notice.put("from", signal.from().toString());
        notice.put("role", grant.role());
        notice.put("kind", signal.kind().wire());
        notice.put("data", signal.data());
        stomp.convertAndSend(UserTopic.of(signal.to()), notice);
    }
}
