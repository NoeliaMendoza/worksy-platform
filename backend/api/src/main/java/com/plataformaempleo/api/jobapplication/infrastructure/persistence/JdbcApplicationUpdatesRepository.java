package com.plataformaempleo.api.jobapplication.infrastructure.persistence;

import com.plataformaempleo.api.jobapplication.domain.model.ApplicationStage;
import com.plataformaempleo.api.jobapplication.domain.model.ApplicationUpdates;
import com.plataformaempleo.api.jobapplication.domain.port.out.ApplicationUpdatesRepository;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcApplicationUpdatesRepository implements ApplicationUpdatesRepository {
    private final JdbcTemplate jdbc;

    JdbcApplicationUpdatesRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ApplicationUpdates inbox(UUID candidateId) {
        long unread = jdbc.queryForObject("""
                select count(*) from applications a join application_stage_history h on h.application_id = a.id
                where a.candidate_id = ? and h.from_stage is not null and h.id > a.stage_seen_history_id
                """, Long.class, candidateId);
        var updates = jdbc.query("""
                select a.id, v.title, c.name, a.stage, max(h.changed_at) as changed_at, count(*) as unread
                from applications a join application_stage_history h on h.application_id = a.id
                join vacancies v on v.id = a.vacancy_id join companies c on c.id = v.company_id
                where a.candidate_id = ? and h.from_stage is not null and h.id > a.stage_seen_history_id
                group by a.id, v.title, c.name, a.stage order by max(h.id) desc limit 50
                """, (rs, n) -> new ApplicationUpdates.Update(rs.getObject("id", UUID.class), rs.getString("title"),
                rs.getString("name"), ApplicationStage.valueOf(rs.getString("stage")),
                rs.getTimestamp("changed_at").toInstant(), rs.getLong("unread")), candidateId);
        return new ApplicationUpdates(unread, updates);
    }

    @Override
    public boolean markSeen(UUID candidateId, UUID applicationId, long lastSeenEventId) {
        return jdbc.update("""
                update applications a set stage_seen_history_id = greatest(stage_seen_history_id, ?)
                where a.id = ? and a.candidate_id = ? and exists (
                    select 1 from application_stage_history h where h.application_id = a.id and h.id = ?)
                """, lastSeenEventId, applicationId, candidateId, lastSeenEventId) == 1;
    }
}
