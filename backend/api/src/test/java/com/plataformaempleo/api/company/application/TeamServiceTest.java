package com.plataformaempleo.api.company.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plataformaempleo.api.company.domain.model.Company;
import com.plataformaempleo.api.company.domain.port.out.CompanyRepository;
import com.plataformaempleo.api.identity.domain.model.PasswordResetToken;
import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.identity.domain.model.User;
import com.plataformaempleo.api.identity.domain.port.out.PasswordHasher;
import com.plataformaempleo.api.identity.domain.port.out.PasswordResetTokenRepository;
import com.plataformaempleo.api.identity.domain.port.out.UserRepository;
import com.plataformaempleo.api.shared.application.Actor;
import com.plataformaempleo.common.domain.DomainException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Empresa y equipo con dobles en memoria. Datos sintéticos. */
class TeamServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC);
    private final Map<UUID, User> userStore = new HashMap<>();
    private final Map<UUID, Company> companyStore = new HashMap<>();
    private final List<PasswordResetToken> tokens = new ArrayList<>();
    private final List<String[]> invitations = new ArrayList<>();
    private TeamService team;
    private CompanyRegistrationService registration;

    @BeforeEach
    void setUp() {
        UserRepository users = new UserRepository() {
            @Override public User save(User u) { userStore.put(u.id(), u); return u; }
            @Override public Optional<User> findById(UUID id) { return Optional.ofNullable(userStore.get(id)); }
            @Override public Optional<User> findActiveByEmail(String email) {
                String n = User.normalizeEmail(email);
                return userStore.values().stream().filter(u -> u.email().equals(n)).findFirst();
            }
            @Override public List<User> findActiveByCompany(UUID companyId) {
                return userStore.values().stream().filter(u -> companyId.equals(u.companyId())).toList();
            }
        };
        CompanyRepository companies = new CompanyRepository() {
            @Override public Company save(Company c) { companyStore.put(c.id(), c); return c; }
            @Override public Optional<Company> findById(UUID id) { return Optional.ofNullable(companyStore.get(id)); }
        };
        PasswordResetTokenRepository resetTokens = new PasswordResetTokenRepository() {
            @Override public PasswordResetToken save(PasswordResetToken t) { tokens.add(t); return t; }
            @Override public Optional<PasswordResetToken> findByHashForUpdate(String h) { return Optional.empty(); }
            @Override public int invalidatePendingForUser(UUID u, Instant now) { return 0; }
        };
        PasswordHasher hasher = new PasswordHasher() {
            @Override public String hash(String raw) { return "hash:" + raw; }
            @Override public boolean matches(String raw, String hash) { return hash.equals("hash:" + raw); }
        };
        registration = new CompanyRegistrationService(companies, users, hasher, clock);
        team = new TeamService(companies, users, resetTokens, hasher,
                (email, company, link, hours) -> invitations.add(new String[] {email, company, link, String.valueOf(hours)}),
                clock, Duration.ofHours(72), "http://localhost:5173/restablecer");
    }

    @Test
    void elAutoregistroCreaLaEmpresaYSuAdministracion() {
        var result = registration.register("  Andina   Cargo ", "Admin@Andina.test", "frase-sintetica-larga");

        assertThat(result.company().name()).isEqualTo("Andina Cargo");
        assertThat(result.admin().role()).isEqualTo(Role.COMPANY_ADMIN);
        assertThat(result.admin().companyId()).isEqualTo(result.company().id());
        assertThat(result.admin().email()).isEqualTo("admin@andina.test");
    }

    @Test
    void elAutoregistroValidaNombreContrasenaYCorreoUnico() {
        registration.register("Andina Cargo", "admin@andina.test", "frase-sintetica-larga");

        assertThat(code(() -> registration.register("A", "otra@andina.test", "frase-sintetica-larga")))
                .isEqualTo("company.name.invalid");
        assertThat(code(() -> registration.register("Otra", "otra@andina.test", "corta")))
                .isEqualTo("user.password.invalid");
        assertThat(code(() -> registration.register("Otra", "ADMIN@andina.test", "frase-sintetica-larga")))
                .isEqualTo("user.email.taken");
        assertThat(companyStore).hasSize(1);
    }

    @Test
    void laAdministracionInvitaYElEnlaceSirveParaActivarLaCuenta() {
        var andina = registration.register("Andina Cargo", "admin@andina.test", "frase-sintetica-larga");

        User recruiter = team.inviteRecruiter(actor(andina.admin()), "Recluta@Andina.test");

        assertThat(recruiter.role()).isEqualTo(Role.RECRUITER);
        assertThat(recruiter.companyId()).isEqualTo(andina.company().id());
        assertThat(invitations).singleElement().satisfies(m -> {
            assertThat(m[0]).isEqualTo("recluta@andina.test");
            assertThat(m[1]).isEqualTo("Andina Cargo");
            assertThat(m[2]).startsWith("http://localhost:5173/restablecer?invitacion=1&token=");
            assertThat(m[3]).isEqualTo("72");
        });
        assertThat(tokens).singleElement().satisfies(t -> {
            assertThat(t.userId()).isEqualTo(recruiter.id());
            assertThat(t.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofHours(72)));
        });
    }

    @Test
    void soloLaAdministracionDeLaEmpresaPuedeInvitar() {
        var andina = registration.register("Andina Cargo", "admin@andina.test", "frase-sintetica-larga");
        User recruiter = team.inviteRecruiter(actor(andina.admin()), "recluta@andina.test");
        User candidate = new User(UUID.randomUUID(), "cand@ejemplo.test", "h", Role.CANDIDATE, null, clock.instant(), null);

        assertThat(code(() -> team.inviteRecruiter(actor(recruiter), "otro@andina.test"))).isEqualTo("access.denied");
        assertThat(code(() -> team.inviteRecruiter(actor(candidate), "otro@andina.test"))).isEqualTo("access.denied");
        assertThat(code(() -> team.members(actor(candidate)))).isEqualTo("access.denied");
    }

    @Test
    void cadaEmpresaSoloVeSuEquipo() {
        var andina = registration.register("Andina Cargo", "admin@andina.test", "frase-sintetica-larga");
        var pacifico = registration.register("Pacífico Retail", "admin@pacifico.test", "frase-sintetica-larga");
        team.inviteRecruiter(actor(andina.admin()), "recluta@andina.test");

        assertThat(team.members(actor(andina.admin()))).extracting(User::email)
                .containsExactlyInAnyOrder("admin@andina.test", "recluta@andina.test");
        assertThat(team.members(actor(pacifico.admin()))).extracting(User::email)
                .containsExactly("admin@pacifico.test");
        assertThat(team.companyOf(actor(pacifico.admin())).name()).isEqualTo("Pacífico Retail");
    }

    private static Actor actor(User u) {
        return new Actor(u.id(), u.role(), u.companyId());
    }

    private static String code(Runnable action) {
        try {
            action.run();
        } catch (DomainException e) {
            return e.code();
        }
        throw new AssertionError("Se esperaba DomainException");
    }

    @Test
    void elNombreDeLaEmpresaTieneLimites() {
        assertThatThrownBy(() -> Company.register(" ", clock)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Company.register("x".repeat(161), clock)).isInstanceOf(DomainException.class);
        assertThat(Company.register("AB", clock).name()).isEqualTo("AB");
    }
}
