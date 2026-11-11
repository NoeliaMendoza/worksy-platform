package com.plataformaempleo.api.identity.infrastructure.security;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciales de los proveedores externos ({@code worksy.oauth.providers.<id>.*}).
 * Un proveedor sin {@code client-id} queda desactivado.
 */
@ConfigurationProperties("worksy.oauth")
public record OAuthProperties(Map<String, Client> providers) {

    public OAuthProperties {
        providers = providers == null ? Map.of() : Map.copyOf(providers);
    }

    public record Client(String clientId, String clientSecret) {

        boolean isConfigured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }

        @Override
        public String toString() {
            return "Client[clientId=" + clientId + ", clientSecret=***]";
        }
    }
}
