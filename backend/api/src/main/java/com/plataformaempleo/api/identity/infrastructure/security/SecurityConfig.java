package com.plataformaempleo.api.identity.infrastructure.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.plataformaempleo.api.identity.application.AuthService;
import com.plataformaempleo.api.identity.application.ExternalLoginService;
import com.plataformaempleo.api.identity.application.PasswordResetService;
import com.plataformaempleo.api.identity.domain.port.out.AccessTokenIssuer;
import com.plataformaempleo.api.identity.domain.port.out.ExternalIdentityRepository;
import com.plataformaempleo.api.identity.domain.port.out.PasswordHasher;
import com.plataformaempleo.api.identity.domain.port.out.PasswordResetMailer;
import com.plataformaempleo.api.identity.domain.port.out.PasswordResetTokenRepository;
import com.plataformaempleo.api.identity.domain.port.out.RefreshTokenRepository;
import com.plataformaempleo.api.identity.domain.port.out.UserRepository;
import com.plataformaempleo.api.identity.infrastructure.web.RefreshCookies;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;

/**
 * API sin estado: cada petición se autentica con el JWT de la cabecera {@code Authorization: Bearer}.
 * El inicio de sesión externo (si hay proveedores configurados) solo usa una sesión HTTP corta para
 * guardar el "state" durante la ida y vuelta al proveedor.
 */
@Configuration
@EnableConfigurationProperties({AuthProperties.class, OAuthProperties.class})
class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, OAuthClients oauthClients, OAuthLoginHandlers oauthHandlers,
                                    RestClient.Builder restClient) throws Exception {
        http
                // Sin CSRF: el JWT viaja en una cabecera, no en cookies. La única cookie (refresh) es
                // SameSite=Strict y está limitada a /api/v1/auth.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout",
                                "/api/v1/auth/password/forgot", "/api/v1/auth/password/reset").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/providers").permitAll()
                        .requestMatchers(OAuthClients.AUTHORIZATION_BASE + "/**", OAuthClients.CALLBACK_BASE + "/**")
                        .permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                // Una petición sin token recibe 401 (nunca una redirección a una página de login).
                .exceptionHandling(e -> e.authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint()))
                // Sin CORS: el frontend llega por el mismo origen (proxy de Vite en desarrollo, Nginx después).
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(roleConverter())));

        if (!oauthClients.isEmpty()) {
            http.oauth2Login(o -> o
                    .clientRegistrationRepository(oauthClients)
                    .authorizedClientService(authorizedClients(oauthClients))
                    // loginPage propia desactiva la página de login que Spring generaría.
                    .loginPage("/api/v1/auth/providers")
                    .authorizationEndpoint(a -> a.baseUri(OAuthClients.AUTHORIZATION_BASE))
                    .redirectionEndpoint(r -> r.baseUri(OAuthClients.CALLBACK_BASE + "/*"))
                    .userInfoEndpoint(u -> u.userService(new GitHubEmailUserService(restClient)))
                    .successHandler(oauthHandlers)
                    .failureHandler(oauthHandlers));
        }
        return http.build();
    }

    @Bean
    OAuthClients oauthClients(OAuthProperties props) {
        return new OAuthClients(props);
    }

    private static OAuth2AuthorizedClientService authorizedClients(OAuthClients clients) {
        return new InMemoryOAuth2AuthorizedClientService(clients);
    }

    @Bean
    OAuthLoginHandlers oauthLoginHandlers(ExternalLoginService externalLogin, RefreshCookies cookies,
                                          AuthProperties props) {
        return new OAuthLoginHandlers(externalLogin, cookies, props.publicUrl());
    }

    @Bean
    SecretKey jwtSigningKey(AuthProperties props) {
        return new SecretKeySpec(props.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key, AuthProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.issuer()));
        return decoder;
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    AuthService authService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordHasher hasher,
                            AccessTokenIssuer accessTokens, Clock clock, AuthProperties props) {
        return new AuthService(users, refreshTokens, hasher, accessTokens, clock, props.refreshTokenTtl());
    }

    @Bean
    PasswordResetService passwordResetService(UserRepository users, PasswordResetTokenRepository resetTokens,
                                              RefreshTokenRepository refreshTokens, PasswordHasher hasher,
                                              PasswordResetMailer mailer, Clock clock, AuthProperties props) {
        return new PasswordResetService(users, resetTokens, refreshTokens, hasher, mailer, clock,
                props.passwordResetTtl(), props.publicUrl() + "/restablecer");
    }

    @Bean
    ExternalLoginService externalLoginService(UserRepository users, ExternalIdentityRepository identities,
                                              PasswordHasher hasher, AuthService auth, Clock clock) {
        return new ExternalLoginService(users, identities, hasher, auth, clock);
    }

    /** Convierte el claim {@code role} en la autoridad {@code ROLE_<ROL>} para usar hasRole(...). */
    private static JwtAuthenticationConverter roleConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString(JwtAccessTokenIssuer.ROLE_CLAIM);
            return role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });
        return converter;
    }
}
