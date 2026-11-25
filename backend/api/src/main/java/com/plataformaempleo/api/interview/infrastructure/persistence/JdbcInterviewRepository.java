package com.plataformaempleo.api.interview.infrastructure.persistence;

import com.plataformaempleo.api.interview.domain.model.ChangeRequest;
import com.plataformaempleo.api.interview.domain.model.Interview;
import com.plataformaempleo.api.interview.domain.port.out.InterviewRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcInterviewRepository implements InterviewRepository {
    private static final String ACTIVE = "('INVITED', 'CONFIRMED')";
    private static final String NO_ID = "00000000-0000-0000-0000-000000000000";

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
        return jdbc.update("update interviews set version = ?, title = ?, starts_at = ?, duration_minutes = ?, zone_id = ?, mode = ?, place = ?, "
                        + "note = ?, status = ?, cancel_reason = ?, outcome_at = ?, updated_at = ? where id = ? and version = ?",
                i.version(), i.title(), Timestamp.from(i.startsAt()), i.durationMinutes(), i.zoneId(), i.mode().name(), i.place(),
                i.note(), i.status().name(), i.cancelReason(), ts(i.outcomeAt()), Timestamp.from(i.updatedAt()), i.id(), expectedVersion) == 1;
    }

    @Override
    public void record(Interview i, Interview.Change change, UUID actorId, Event.Actor actor, String reason, Boolean placeChanged) {
        jdbc.update("insert into interview_events (interview_id, version, change, starts_at, duration_minutes, zone_id, changed_by, changed_at, "
                        + "actor_role, reason, place_changed) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                i.id(), i.version(), change.name(), Timestamp.from(i.startsAt()), i.durationMinutes(), i.zoneId(), actorId,
                Timestamp.from(i.updatedAt()), actor.name(), reason, placeChanged);
    }

    private static final String SELECT = "select i.*, v.title as vacancy_title, c.name as company_name, u.email as candidate_email, "
            + "org.email as organizer_email, i.version > coalesce(r.version, -1) as unread, "
            + "exists (select 1 from interview_participants mp where mp.interview_id = i.id and mp.user_id = ?) as mine, "
            + "cr.id as cr_id, cr.reason as cr_reason, cr.slots::text as cr_slots, cr.created_at as cr_created";

    private static final String JOINS = " from interviews i join applications a on a.id = i.application_id "
            + "join vacancies v on v.id = a.vacancy_id join companies c on c.id = v.company_id "
            + "join users u on u.id = a.candidate_id join candidate_profiles p on p.user_id = a.candidate_id "
            + "left join interview_reads r on r.interview_id = i.id and r.user_id = ? "
            + "left join lateral (select ou.email from interview_participants op join users ou on ou.id = op.user_id "
            + "  where op.interview_id = i.id and op.role = 'ORGANIZER' limit 1) org on true "
            + "left join interview_change_requests cr on cr.interview_id = i.id and cr.status = 'OPEN' ";

    /** Solo las citas que puede ver quien consulta: las propias o, para la empresa, las de candidatos que comparten sus datos. */
    private static String scope(Viewer viewer, boolean mineOnly, List<Object> args) {
        String where = viewer.company() ? "v.company_id = ? and p.consent_share = true" : "a.candidate_id = ?";
        args.add(viewer.company() ? viewer.companyId() : viewer.userId());
        if (mineOnly && viewer.company()) {
            where += " and exists (select 1 from interview_participants fp where fp.interview_id = i.id and fp.user_id = ?)";
            args.add(viewer.userId());
        }
        return where;
    }

    @Override
    public Optional<Item> item(UUID id, Viewer viewer, Instant now) {
        List<Object> args = new ArrayList<>(List.of(viewer.userId(), viewer.userId()));
        String where = scope(viewer, false, args) + " and i.id = ?";
        args.add(id);
        return jdbc.query(SELECT + JOINS + "where " + where, (rs, n) -> item(rs, viewer, now), args.toArray()).stream().findFirst();
    }

    @Override
    public List<Item> list(Viewer viewer, Query q) {
        List<Object> args = new ArrayList<>(List.of(viewer.userId(), viewer.userId()));
        String where = scope(viewer, q.mineOnly(), args);
        Timestamp now = Timestamp.from(q.now());
        String order;
        switch (q.view()) {
            case upcoming -> {
                where += " and i.starts_at > ? and i.status in " + ACTIVE;
                args.add(now);
                order = "i.starts_at asc, i.id asc";
            }
            case pending -> {
                where += " and i.starts_at > ? and i.status = 'INVITED'";
                args.add(now);
                order = "i.starts_at asc, i.id asc";
            }
            case history -> {
                where += " and ((i.starts_at <= ? and i.status <> 'CANCELLED') or i.status in ('DECLINED', 'COMPLETED', 'NO_SHOW'))";
                args.add(now);
                order = "i.starts_at desc, i.id desc";
            }
            case cancelled -> {
                where += " and i.status = 'CANCELLED'";
                order = "i.starts_at desc, i.id desc";
            }
            case range -> {
                where += " and i.starts_at >= ? and i.starts_at < ?";
                args.add(Timestamp.from(q.from()));
                args.add(Timestamp.from(q.to()));
                order = "i.starts_at asc, i.id asc";
            }
            default -> {
                where += " and i.version > coalesce(r.version, -1)";
                order = "i.updated_at desc, i.id desc";
            }
        }
        args.add(q.limit());
        args.add(q.offset());
        return jdbc.query(SELECT + JOINS + "where " + where + " order by " + order + " limit ? offset ?",
                (rs, n) -> item(rs, viewer, q.now()), args.toArray());
    }

    @Override
    public Counts counts(Viewer viewer, boolean mineOnly, Instant now) {
        Timestamp at = Timestamp.from(now);
        List<Object> args = new ArrayList<>(List.of(at, at, at, at, at, viewer.userId()));
        String where = scope(viewer, mineOnly, args);
        return jdbc.queryForObject("select "
                        + "count(*) filter (where i.starts_at > ? and i.status in " + ACTIVE + ") as upcoming, "
                        + "count(*) filter (where i.starts_at > ? and i.status = 'INVITED') as pending, "
                        + "count(*) filter (where (i.starts_at <= ? and i.status <> 'CANCELLED') or i.status in ('DECLINED', 'COMPLETED', 'NO_SHOW')) as history, "
                        + "count(*) filter (where i.status = 'CANCELLED') as cancelled, "
                        + "count(*) filter (where cr.id is not null) as change_requests, "
                        + "count(*) filter (where i.status = 'CONFIRMED' and i.starts_at <= ?) as awaiting, "
                        + "count(*) filter (where i.status = 'INVITED' and i.starts_at <= ?) as expired"
                        + JOINS + "where " + where,
                (rs, n) -> new Counts(rs.getLong("upcoming"), rs.getLong("pending"), rs.getLong("history"), rs.getLong("cancelled"),
                        rs.getLong("change_requests"), rs.getLong("awaiting"), rs.getLong("expired")), args.toArray());
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
        return jdbc.query("select version, change, starts_at, duration_minutes, zone_id, changed_at, actor_role, reason, place_changed "
                        + "from interview_events where interview_id = ? order by version",
                (rs, n) -> new Event(rs.getInt("version"), Interview.Change.valueOf(rs.getString("change")), rs.getTimestamp("starts_at").toInstant(),
                        rs.getInt("duration_minutes"), rs.getString("zone_id"), rs.getTimestamp("changed_at").toInstant(),
                        rs.getString("actor_role") == null ? null : Event.Actor.valueOf(rs.getString("actor_role")),
                        rs.getString("reason"), rs.getObject("place_changed", Boolean.class)), interviewId);
    }

    @Override
    public void addParticipant(UUID interviewId, UUID userId, String role) {
        jdbc.update("insert into interview_participants (interview_id, user_id, role) values (?, ?, ?) on conflict do nothing",
                interviewId, userId, role);
    }

    private static final String OVERLAP = " i.status in " + ACTIVE + " and i.starts_at < ? "
            + "and i.starts_at + i.duration_minutes * interval '1 minute' > ? and i.id <> coalesce(?::uuid, '" + NO_ID + "'::uuid)";

    @Override
    public boolean overlapsApplication(UUID applicationId, Instant start, Instant end, UUID exclude) {
        // Bloquea la postulación hasta el final de la transacción: dos agendas simultáneas no pueden cruzarse.
        jdbc.query("select id from applications where id = ? for update", (rs, n) -> rs.getObject(1), applicationId);
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from interviews i where i.application_id = ? and" + OVERLAP + ")",
                Boolean.class, applicationId, Timestamp.from(end), Timestamp.from(start), exclude));
    }

    @Override
    public List<Overlap> overlapsParticipant(UUID userId, Instant start, Instant end, UUID exclude) {
        return jdbc.query("select i.id, i.title, i.starts_at, i.duration_minutes, i.zone_id, v.title as vacancy_title from interviews i "
                        + "join interview_participants pp on pp.interview_id = i.id and pp.user_id = ? "
                        + "join applications a on a.id = i.application_id join vacancies v on v.id = a.vacancy_id where" + OVERLAP
                        + " order by i.starts_at limit 5",
                (rs, n) -> new Overlap(rs.getObject("id", UUID.class), rs.getString("title"), rs.getTimestamp("starts_at").toInstant(),
                        rs.getInt("duration_minutes"), rs.getString("zone_id"), rs.getString("vacancy_title")),
                userId, Timestamp.from(end), Timestamp.from(start), exclude);
    }

    @Override
    public boolean insertChangeRequest(ChangeRequest r) {
        String slots = r.slots().stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(",", "[", "]"));
        return jdbc.update("insert into interview_change_requests (id, interview_id, requested_by, reason, slots, status, created_at) "
                        + "values (?, ?, ?, ?, ?::jsonb, 'OPEN', ?) on conflict do nothing",
                r.id(), r.interviewId(), r.requestedBy(), r.reason(), slots, Timestamp.from(r.createdAt())) == 1;
    }

    @Override
    public Optional<ChangeRequest> openChangeRequest(UUID interviewId) {
        return jdbc.query("select id, interview_id, requested_by, reason, slots::text as slots, status, created_at "
                        + "from interview_change_requests where interview_id = ? and status = 'OPEN'",
                (rs, n) -> new ChangeRequest(rs.getObject("id", UUID.class), rs.getObject("interview_id", UUID.class),
                        rs.getObject("requested_by", UUID.class), rs.getString("reason"), slots(rs.getString("slots")),
                        ChangeRequest.Status.valueOf(rs.getString("status")), rs.getTimestamp("created_at").toInstant()),
                interviewId).stream().findFirst();
    }

    @Override
    public boolean resolveChangeRequest(UUID requestId, ChangeRequest.Status status, UUID resolvedBy, Instant at) {
        return jdbc.update("update interview_change_requests set status = ?, resolved_by = ?, resolved_at = ? where id = ? and status = 'OPEN'",
                status.name(), resolvedBy, Timestamp.from(at), requestId) == 1;
    }

    private static Item item(ResultSet rs, Viewer viewer, Instant now) throws SQLException {
        Interview interview = interview(rs);
        UUID requestId = rs.getObject("cr_id", UUID.class);
        OpenChange change = requestId == null ? null : new OpenChange(requestId, rs.getString("cr_reason"),
                slots(rs.getString("cr_slots")), rs.getTimestamp("cr_created").toInstant());
        return new Item(interview, rs.getString("vacancy_title"), rs.getString("company_name"),
                viewer.company() ? rs.getString("candidate_email") : null,
                viewer.company() ? rs.getString("organizer_email") : null,
                rs.getBoolean("unread"), rs.getBoolean("mine"), interview.phase(now), change);
    }

    /** {@code ["2026-11-30T20:00:00Z", ...]} tal como lo devuelve PostgreSQL (solo instantes ISO, sin escapes). */
    private static List<Instant> slots(String json) {
        String inner = json.trim().replaceAll("^\\[|]$", "");
        if (inner.isBlank()) return List.of();
        return Arrays.stream(inner.split(",")).map(s -> Instant.parse(s.trim().replace("\"", ""))).toList();
    }

    private static Timestamp ts(Instant instant) { return instant == null ? null : Timestamp.from(instant); }

    private static Interview interview(ResultSet rs) throws SQLException {
        Timestamp outcome = rs.getTimestamp("outcome_at");
        return new Interview(rs.getObject("id", UUID.class), rs.getObject("application_id", UUID.class), rs.getInt("version"),
                rs.getString("title"), rs.getTimestamp("starts_at").toInstant(), rs.getInt("duration_minutes"), rs.getString("zone_id"),
                Interview.Mode.valueOf(rs.getString("mode")), rs.getString("place"), rs.getString("note"),
                Interview.Status.valueOf(rs.getString("status")), rs.getString("cancel_reason"),
                outcome == null ? null : outcome.toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
}
