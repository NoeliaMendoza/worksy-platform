package com.plataformaempleo.api.company.infrastructure.web;

import com.plataformaempleo.api.company.application.CompanyRegistrationService;
import com.plataformaempleo.api.company.application.TeamService;
import com.plataformaempleo.api.company.domain.model.Company;
import com.plataformaempleo.api.identity.domain.model.User;
import com.plataformaempleo.api.shared.infrastructure.web.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Adaptador de entrada REST: registro de empresas y su equipo. */
@RestController
@RequestMapping("/api/v1/companies")
@Tag(name = "Empresas", description = "Registro de empresas y gestión de su equipo de selección")
class CompanyController {

    private final CompanyRegistrationService registration;
    private final TeamService team;

    CompanyController(CompanyRegistrationService registration, TeamService team) {
        this.registration = registration;
        this.team = team;
    }

    @PostMapping
    @Operation(summary = "Registrar una empresa",
            description = "Crea la empresa y la cuenta de su administrador/a. Después se inicia sesión con /api/v1/auth/login.")
    @ApiResponse(responseCode = "201", description = "Empresa registrada")
    @ApiResponse(responseCode = "400", description = "Nombre, correo o contraseña no válidos")
    @ApiResponse(responseCode = "409", description = "Ya existe una cuenta con ese correo")
    ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegisterCompanyRequest body) {
        var result = registration.register(body.companyName(), body.email(), body.password());
        return ResponseEntity.created(URI.create("/api/v1/companies/me"))
                .body(new RegistrationResponse(CompanyResponse.from(result.company()), MemberResponse.from(result.admin())));
    }

    @GetMapping("/me")
    @Operation(summary = "Empresa de la cuenta actual", security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "Datos de la empresa")
    @ApiResponse(responseCode = "403", description = "La cuenta no pertenece a una empresa")
    CompanyResponse myCompany(@AuthenticationPrincipal Jwt jwt) {
        return CompanyResponse.from(team.companyOf(CurrentActor.from(jwt)));
    }

    @GetMapping("/me/members")
    @Operation(summary = "Equipo de la empresa", security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "Personas activas de la empresa")
    @ApiResponse(responseCode = "403", description = "La cuenta no pertenece a una empresa")
    List<MemberResponse> members(@AuthenticationPrincipal Jwt jwt) {
        return team.members(CurrentActor.from(jwt)).stream().map(MemberResponse::from).toList();
    }

    @PostMapping("/me/members")
    @Operation(summary = "Invitar a un reclutador",
            description = "Crea la cuenta y envía por correo un enlace de un solo uso para elegir la contraseña.",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "201", description = "Invitación enviada")
    @ApiResponse(responseCode = "403", description = "Solo la administración de la empresa puede invitar")
    @ApiResponse(responseCode = "409", description = "Ya existe una cuenta con ese correo")
    ResponseEntity<MemberResponse> invite(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody InviteRequest body) {
        User recruiter = team.inviteRecruiter(CurrentActor.from(jwt), body.email());
        return ResponseEntity.status(201).body(MemberResponse.from(recruiter));
    }

    // ---- Contratos JSON ----

    @Schema(name = "RegisterCompanyRequest")
    record RegisterCompanyRequest(
            @Schema(example = "Andina Cargo") @NotBlank @Size(max = 160) String companyName,
            @Schema(example = "seleccion@empresa.test") @NotBlank @Size(max = 254) String email,
            @Schema(minLength = 10, maxLength = 128) @NotBlank String password) {

        @Override
        public String toString() {
            return "RegisterCompanyRequest[companyName=" + companyName + ", email=" + email + ", password=***]";
        }
    }

    @Schema(name = "InviteRequest")
    record InviteRequest(@Schema(example = "reclutadora@empresa.test") @NotBlank @Size(max = 254) String email) {
    }

    @Schema(name = "CompanyResponse")
    record CompanyResponse(UUID id, String name, Instant createdAt) {
        static CompanyResponse from(Company c) {
            return new CompanyResponse(c.id(), c.name(), c.createdAt());
        }
    }

    @Schema(name = "MemberResponse")
    record MemberResponse(UUID id, String email, String role, Instant createdAt) {
        static MemberResponse from(User u) {
            return new MemberResponse(u.id(), u.email(), u.role().name(), u.createdAt());
        }
    }

    @Schema(name = "CompanyRegistrationResponse")
    record RegistrationResponse(CompanyResponse company, MemberResponse admin) {
    }
}
