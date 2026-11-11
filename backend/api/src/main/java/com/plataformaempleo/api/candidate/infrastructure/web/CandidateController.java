package com.plataformaempleo.api.candidate.infrastructure.web;

import com.plataformaempleo.api.candidate.application.ConsentService;
import com.plataformaempleo.api.candidate.application.CvService;
import com.plataformaempleo.api.candidate.infrastructure.web.CandidateDtos.ConsentsRequest;
import com.plataformaempleo.api.candidate.infrastructure.web.CandidateDtos.ConsentsResponse;
import com.plataformaempleo.api.candidate.infrastructure.web.CandidateDtos.CvResponse;
import com.plataformaempleo.api.shared.infrastructure.web.CurrentActor;
import com.plataformaempleo.common.domain.DomainException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Adaptador de entrada REST del candidato: sus CV y sus consentimientos (sección 6 del diseño). */
@RestController
@RequestMapping("/api/v1/candidates/me")
@Tag(name = "Candidato", description = "CV y consentimientos del candidato que inició sesión")
class CandidateController {

    private final CvService cvs;
    private final ConsentService consents;

    CandidateController(CvService cvs, ConsentService consents) {
        this.cvs = cvs;
        this.consents = consents;
    }

    @PostMapping(path = "/cv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Subir un CV en PDF", description = "Máximo 5 MB. Se valida que el contenido sea un PDF real.",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "201", description = "CV guardado")
    @ApiResponse(responseCode = "400", description = "Archivo vacío, no es PDF o se alcanzó el máximo de CV")
    @ApiResponse(responseCode = "403", description = "La cuenta no es de candidato")
    @ApiResponse(responseCode = "409", description = "Ya subió este mismo archivo")
    @ApiResponse(responseCode = "413", description = "El archivo supera los 5 MB")
    @ApiResponse(responseCode = "503", description = "El almacenamiento no está disponible")
    ResponseEntity<CvResponse> upload(@AuthenticationPrincipal Jwt jwt, @RequestPart("file") MultipartFile file) {
        var created = cvs.upload(CurrentActor.from(jwt), bytesOf(file));
        return ResponseEntity.created(URI.create("/api/v1/candidates/me/cv/" + created.id()))
                .body(CvResponse.from(created));
    }

    @GetMapping("/cv")
    @Operation(summary = "Mis CV", description = "Del más reciente al más antiguo.",
            security = @SecurityRequirement(name = "bearer"))
    List<CvResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return cvs.list(CurrentActor.from(jwt)).stream().map(CvResponse::from).toList();
    }

    @DeleteMapping("/cv/{id}")
    @Operation(summary = "Eliminar un CV", description = "Borra el archivo del almacenamiento y su registro.",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "204", description = "CV eliminado")
    @ApiResponse(responseCode = "404", description = "No existe entre los CV del candidato")
    ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        cvs.delete(CurrentActor.from(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/consents")
    @Operation(summary = "Mis consentimientos", security = @SecurityRequirement(name = "bearer"))
    ConsentsResponse getConsents(@AuthenticationPrincipal Jwt jwt) {
        return ConsentsResponse.from(consents.get(CurrentActor.from(jwt)));
    }

    @PutMapping("/consents")
    @Operation(summary = "Actualizar mis consentimientos", security = @SecurityRequirement(name = "bearer"))
    ConsentsResponse updateConsents(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ConsentsRequest body) {
        return ConsentsResponse.from(consents.update(CurrentActor.from(jwt), body.shareWithRecruiters(),
                body.githubLink()));
    }

    private static byte[] bytesOf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new DomainException("cv.file.unreadable", "No se pudo leer el archivo. Intenta subirlo de nuevo");
        }
    }
}
