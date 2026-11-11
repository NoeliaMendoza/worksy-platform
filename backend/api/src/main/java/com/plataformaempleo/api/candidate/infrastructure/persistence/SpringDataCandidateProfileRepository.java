package com.plataformaempleo.api.candidate.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataCandidateProfileRepository extends JpaRepository<CandidateProfileJpaEntity, UUID> {
}
