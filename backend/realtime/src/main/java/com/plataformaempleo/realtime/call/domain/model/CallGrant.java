package com.plataformaempleo.realtime.call.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Permiso de una persona para hablar en una sala, tal como lo emitió la API en el token de sala:
 * con quién puede intercambiar señales ({@code peers}) y hasta cuándo.
 */
public record CallGrant(UUID userId, UUID sessionId, UUID interviewId, String role, List<UUID> peers, Instant expiresAt) {
    public CallGrant {
        peers = List.copyOf(peers);
    }

    public boolean allows(UUID peer) {
        return peers.contains(peer);
    }

    public boolean expired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
