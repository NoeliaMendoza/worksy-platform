package com.plataformaempleo.api.screening.infrastructure.persistence;

import com.plataformaempleo.api.screening.domain.model.RankingResult;
import com.plataformaempleo.api.screening.domain.model.ScreeningRequest;
import com.plataformaempleo.api.screening.domain.port.out.RankingRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRankingRepository implements RankingRepository {
    private final JdbcTemplate jdbc;

    JdbcRankingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<RankingResult> findSharedByVacancy(UUID vacancyId, String provider) {
        Map<UUID, List<RankingResult.Answer>> answers = new HashMap<>();
        jdbc.query("""
                select r.id, s.question_key, s.choice, s.score, s.probability
                from screening_answers s join screening_requests r on r.id = s.request_id
                join applications a on a.id = r.application_id
                join candidate_profiles p on p.user_id = a.candidate_id
                where r.vacancy_id = ? and r.provider = ? and p.consent_share = true
                order by s.question_key
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                answers.computeIfAbsent(rs.getObject("id", UUID.class), key -> new ArrayList<>())
                        .add(new RankingResult.Answer(rs.getString("question_key"), rs.getString("choice"),
                                rs.getBigDecimal("score"), rs.getBigDecimal("probability"))), vacancyId, provider);
        return jdbc.query("""
                select a.id, a.applied_at, p.full_name, p.headline, p.location, u.email,
                       r.id as request_id, r.status, r.model_version, r.overall_score, r.confidence, d.decision
                from applications a join candidate_profiles p on p.user_id = a.candidate_id
                join users u on u.id = a.candidate_id
                left join screening_requests r on r.application_id = a.id and r.provider = ?
                left join application_reviews d on d.application_id = a.id
                where a.vacancy_id = ? and p.consent_share = true
                """, (rs, n) -> new RankingResult(rs.getObject("id", UUID.class), rs.getString("full_name"),
                rs.getString("email"), rs.getString("headline"), rs.getString("location"),
                rs.getTimestamp("applied_at").toInstant(),
                rs.getString("status") == null ? null : ScreeningRequest.Status.valueOf(rs.getString("status")),
                rs.getString("model_version"), rs.getBigDecimal("overall_score"), rs.getBigDecimal("confidence"),
                List.copyOf(answers.getOrDefault(rs.getObject("request_id", UUID.class), List.of())),
                rs.getString("decision") == null ? null : RankingResult.Decision.valueOf(rs.getString("decision"))),
                provider, vacancyId);
    }

    @Override
    public void saveDecision(UUID applicationId, RankingResult.Decision decision, UUID reviewerId, Instant at) {
        jdbc.update("""
                insert into application_reviews (application_id, decision, reviewed_by, reviewed_at)
                values (?, ?, ?, ?) on conflict (application_id) do update
                set decision = excluded.decision, reviewed_by = excluded.reviewed_by, reviewed_at = excluded.reviewed_at
                """, applicationId, decision == null ? null : decision.name(), reviewerId, Timestamp.from(at));
    }
}
