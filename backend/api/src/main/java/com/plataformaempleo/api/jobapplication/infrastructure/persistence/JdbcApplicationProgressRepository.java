package com.plataformaempleo.api.jobapplication.infrastructure.persistence;

import com.plataformaempleo.api.jobapplication.domain.model.ApplicationProgress;
import com.plataformaempleo.api.jobapplication.domain.model.ApplicationStage;
import com.plataformaempleo.api.jobapplication.domain.port.out.ApplicationProgressRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.OptionalLong;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcApplicationProgressRepository implements ApplicationProgressRepository {
    private final JdbcTemplate jdbc;

    JdbcApplicationProgressRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ApplicationStage stage(UUID applicationId) {
        return ApplicationStage.valueOf(jdbc.queryForObject(
                "select stage from applications where id = ?", String.class, applicationId));
    }

    @Override
    public ApplicationProgress find(UUID applicationId) {
        var history = jdbc.query("select id, from_stage, stage, message, changed_at from application_stage_history "
                        + "where application_id = ? order by id",
                (rs, n) -> new ApplicationProgress.Event(rs.getLong("id"),
                        rs.getString("from_stage") == null ? null : ApplicationStage.valueOf(rs.getString("from_stage")),
                        ApplicationStage.valueOf(rs.getString("stage")), rs.getString("message"),
                        rs.getTimestamp("changed_at").toInstant()), applicationId);
        return new ApplicationProgress(stage(applicationId), history);
    }

    @Override
    public void recordReceived(UUID applicationId, UUID candidateId, Instant now) {
        record(applicationId, null, ApplicationStage.RECEIVED, candidateId, null, now);
    }

    @Override
    public Map<UUID, List<ApplicationProgress.Milestone>> milestonesForCandidate(UUID candidateId) {
        Map<UUID, List<ApplicationProgress.Milestone>> result = new LinkedHashMap<>();
        jdbc.query("select h.application_id, h.stage, h.changed_at from application_stage_history h "
                + "join applications a on a.id = h.application_id where a.candidate_id = ? order by h.id", rs -> {
            result.computeIfAbsent(rs.getObject("application_id", UUID.class), ignored -> new ArrayList<>())
                    .add(new ApplicationProgress.Milestone(ApplicationStage.valueOf(rs.getString("stage")),
                            rs.getTimestamp("changed_at").toInstant()));
        }, candidateId);
        return result;
    }

    @Override
    @Transactional
    public OptionalLong move(UUID applicationId, ApplicationStage expected, ApplicationStage next,
                        UUID changedBy, String message, Instant now) {
        int changed = jdbc.update("update applications set stage = ? where id = ? and stage = ?",
                next.name(), applicationId, expected.name());
        if (changed == 0) return OptionalLong.empty();
        return OptionalLong.of(record(applicationId, expected, next, changedBy, message, now));
    }

    private long record(UUID applicationId, ApplicationStage from, ApplicationStage next,
                        UUID actor, String message, Instant now) {
        return jdbc.queryForObject("insert into application_stage_history "
                        + "(application_id, from_stage, stage, changed_by, message, changed_at) values (?, ?, ?, ?, ?, ?) returning id",
                Long.class,
                applicationId, from == null ? null : from.name(), next.name(), actor, message, Timestamp.from(now));
    }
}
