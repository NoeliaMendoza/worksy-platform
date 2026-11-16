package com.plataformaempleo.api.conversation.infrastructure.persistence;

import com.plataformaempleo.api.conversation.domain.model.ConversationSummary;
import com.plataformaempleo.api.conversation.domain.model.Message;
import com.plataformaempleo.api.conversation.domain.port.out.MessageRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Conversaciones en PostgreSQL ({@code application_messages} y {@code conversation_reads}, migración V12). */
@Repository
class JdbcMessageRepository implements MessageRepository {

    /**
     * Última línea de cada conversación y cuántos mensajes de otras personas llegaron después de la última
     * lectura de quien consulta. {@code %s} es el filtro por persona candidata o por empresa.
     */
    private static final String SUMMARIES = """
            select a.id as application_id, v.title as vacancy_title, c.name as company_name,
                   case when ? and p.consent_share then u.email end as candidate_email,
                   m.body, m.sent_at, m.from_candidate,
                   (select count(*) from application_messages x
                     where x.application_id = a.id and x.sender_id <> ?
                       and x.sent_at > coalesce(r.last_read_at, '-infinity'::timestamptz)) as unread
            from applications a
            join vacancies v on v.id = a.vacancy_id
            join companies c on c.id = v.company_id
            join users u on u.id = a.candidate_id
            left join candidate_profiles p on p.user_id = a.candidate_id
            join lateral (select body, sent_at, from_candidate from application_messages
                           where application_id = a.id order by sent_at desc, id desc limit 1) m on true
            left join conversation_reads r on r.application_id = a.id and r.user_id = ?
            where %s
            order by m.sent_at desc
            """;

    private final JdbcTemplate jdbc;

    JdbcMessageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Message save(Message m) {
        jdbc.update("insert into application_messages (id, application_id, sender_id, from_candidate, body, sent_at) "
                        + "values (?, ?, ?, ?, ?, ?)",
                m.id(), m.applicationId(), m.senderId(), m.fromCandidate(), m.body(), Timestamp.from(m.sentAt()));
        return m;
    }

    @Override
    public List<Message> findByApplication(UUID applicationId, Instant before, int limit) {
        if (before == null) {
            return jdbc.query("select * from application_messages where application_id = ? "
                    + "order by sent_at desc, id desc limit ?", JdbcMessageRepository::message, applicationId, limit);
        }
        return jdbc.query("select * from application_messages where application_id = ? and sent_at < ? "
                        + "order by sent_at desc, id desc limit ?", JdbcMessageRepository::message,
                applicationId, Timestamp.from(before), limit);
    }

    @Override
    public boolean hasCompanyMessage(UUID applicationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from application_messages "
                + "where application_id = ? and not from_candidate)", Boolean.class, applicationId));
    }

    @Override
    public void markRead(UUID applicationId, UUID userId, Instant at) {
        jdbc.update("""
                insert into conversation_reads (application_id, user_id, last_read_at) values (?, ?, ?)
                on conflict (application_id, user_id)
                do update set last_read_at = greatest(conversation_reads.last_read_at, excluded.last_read_at)
                """, applicationId, userId, Timestamp.from(at));
    }

    @Override
    public List<ConversationSummary> summariesForCandidate(UUID candidateId) {
        return jdbc.query(SUMMARIES.formatted("a.candidate_id = ?"), JdbcMessageRepository::summary,
                false, candidateId, candidateId, candidateId);
    }

    @Override
    public List<ConversationSummary> summariesForCompany(UUID companyId, UUID userId) {
        return jdbc.query(SUMMARIES.formatted("v.company_id = ?"), JdbcMessageRepository::summary,
                true, userId, userId, companyId);
    }

    private static Message message(ResultSet rs, int row) throws SQLException {
        return new Message(rs.getObject("id", UUID.class), rs.getObject("application_id", UUID.class),
                rs.getObject("sender_id", UUID.class), rs.getBoolean("from_candidate"), rs.getString("body"),
                rs.getTimestamp("sent_at").toInstant());
    }

    private static ConversationSummary summary(ResultSet rs, int row) throws SQLException {
        return new ConversationSummary(rs.getObject("application_id", UUID.class), rs.getString("vacancy_title"),
                rs.getString("company_name"), rs.getString("candidate_email"), rs.getString("body"),
                rs.getTimestamp("sent_at").toInstant(), rs.getBoolean("from_candidate"), rs.getInt("unread"));
    }
}
