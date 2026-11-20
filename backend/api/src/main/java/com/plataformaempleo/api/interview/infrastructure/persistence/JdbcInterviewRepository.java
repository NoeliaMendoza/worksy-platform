package com.plataformaempleo.api.interview.infrastructure.persistence;

import com.plataformaempleo.api.interview.domain.model.Interview;
import com.plataformaempleo.api.interview.domain.port.out.InterviewRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcInterviewRepository implements InterviewRepository {
    private final JdbcTemplate jdbc;
    JdbcInterviewRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<Interview> find(UUID id) {
        return jdbc.query("select * from interviews where id = ?", (rs, n) -> interview(rs), id).stream().findFirst();
    }

    @Override
    public boolean insert(Interview i) {
        return jdbc.update("insert into interviews (id, application_id, version, title, starts_at, duration_minutes, zone_id, mode, place, note, status, updated_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) on conflict (id) do nothing",
                i.id(), i.applicationId(), i.version(), i.title(), Timestamp.from(i.startsAt()), i.durationMinutes(), i.zoneId(),
                i.mode().name(), i.place(), i.note(), i.status().name(), Timestamp.from(i.updatedAt())) == 1;
    }

    @Override
    public boolean update(Interview i, int expectedVersion) {
        return jdbc.update("update interviews set version = ?, title = ?, starts_at = ?, duration_minutes = ?, zone_id = ?, mode = ?, place = ?, note = ?, status = ?, updated_at = ? "
                        + "where id = ? and version = ?", i.version(), i.title(), Timestamp.from(i.startsAt()), i.durationMinutes(),
                i.zoneId(), i.mode().name(), i.place(), i.note(), i.status().name(), Timestamp.from(i.updatedAt()), i.id(), expectedVersion) == 1;
    }

    @Override
    public void record(Interview i, Interview.Change change, UUID actorId) {
        jdbc.update("insert into interview_events (interview_id, version, change, starts_at, duration_minutes, zone_id, changed_by, changed_at) values (?, ?, ?, ?, ?, ?, ?, ?)",
                i.id(), i.version(), change.name(), Timestamp.from(i.startsAt()), i.durationMinutes(), i.zoneId(), actorId, Timestamp.from(i.updatedAt()));
    }

    private static final String JOINS = " from interviews i join applications a on a.id = i.application_id "
            + "join vacancies v on v.id = a.vacancy_id join companies c on c.id = v.company_id "
            + "join users u on u.id = a.candidate_id join candidate_profiles p on p.user_id = a.candidate_id "
            + "left join interview_reads r on r.interview_id = i.id and r.user_id = ? ";

    @Override
    public List<Item> list(UUID userId, UUID companyId, Interview.View view, Instant now, int offset, int limit) {
        List<Object> args = new ArrayList<>(List.of(userId, companyId == null ? userId : companyId));
        String where = companyId == null ? "a.candidate_id = ?" : "v.company_id = ? and p.consent_share = true";
        if (view == Interview.View.upcoming) {
            where += " and i.starts_at > ? and i.status in ('INVITED', 'CONFIRMED')";
            args.add(Timestamp.from(now));
        } else if (view == Interview.View.history) {
            where += " and (i.starts_at <= ? or i.status in ('DECLINED', 'CANCELLED'))";
            args.add(Timestamp.from(now));
        } else where += " and i.version > coalesce(r.version, -1)";
        String order = view == Interview.View.upcoming ? "i.starts_at asc, i.id asc" : "i.updated_at desc, i.id desc";
        args.add(limit);
        args.add(offset);
        return jdbc.query("select i.*, v.title as vacancy_title, c.name as company_name, u.email as candidate_email, "
                        + "i.version > coalesce(r.version, -1) as unread" + JOINS + "where " + where + " order by " + order + " limit ? offset ?",
                (rs, n) -> new Item(interview(rs), rs.getString("vacancy_title"), rs.getString("company_name"), rs.getString("candidate_email"), rs.getBoolean("unread")), args.toArray());
    }

    @Override
    public long unread(UUID userId, UUID companyId) {
        String where = companyId == null ? "a.candidate_id = ?" : "v.company_id = ? and p.consent_share = true";
        return jdbc.queryForObject("select count(*)" + JOINS + "where " + where + " and i.version > coalesce(r.version, -1)",
                Long.class, userId, companyId == null ? userId : companyId);
    }

    @Override
    public void markRead(UUID interviewId, UUID userId, int version) {
        jdbc.update("insert into interview_reads (interview_id, user_id, version) values (?, ?, ?) "
                + "on conflict (interview_id, user_id) do update set version = greatest(interview_reads.version, excluded.version)", interviewId, userId, version);
    }

    @Override
    public List<Event> history(UUID interviewId) {
        return jdbc.query("select version, change, starts_at, duration_minutes, zone_id, changed_at from interview_events where interview_id = ? order by version",
                (rs, n) -> new Event(rs.getInt("version"), Interview.Change.valueOf(rs.getString("change")), rs.getTimestamp("starts_at").toInstant(),
                        rs.getInt("duration_minutes"), rs.getString("zone_id"), rs.getTimestamp("changed_at").toInstant()), interviewId);
    }

    private static Interview interview(ResultSet rs) throws SQLException {
        return new Interview(rs.getObject("id", UUID.class), rs.getObject("application_id", UUID.class), rs.getInt("version"),
                rs.getString("title"), rs.getTimestamp("starts_at").toInstant(), rs.getInt("duration_minutes"), rs.getString("zone_id"),
                Interview.Mode.valueOf(rs.getString("mode")), rs.getString("place"), rs.getString("note"),
                Interview.Status.valueOf(rs.getString("status")), rs.getTimestamp("updated_at").toInstant());
    }
}
