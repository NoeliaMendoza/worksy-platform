package com.plataformaempleo.api.shared.infrastructure.persistence;

import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.shared.domain.model.AuditAction;
import com.plataformaempleo.api.shared.domain.model.AuditEntry;
import com.plataformaempleo.api.shared.domain.model.AuditPage;
import com.plataformaempleo.api.shared.domain.port.out.AuditTrail;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcAuditTrail implements AuditTrail {
    private static final String FROM = " from audit_events e join users u on u.id = e.actor_id "
            + "left join companies c on c.id = e.company_id ";
    private static final String FILTER = """
            where (? = '' or strpos(lower(u.email), ?) > 0 or strpos(lower(coalesce(c.name, '')), ?) > 0
                   or strpos(lower(e.target_label), ?) > 0)
              and (? = '' or e.action = ?)
            """;
    private final JdbcTemplate jdbc;

    JdbcAuditTrail(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(UUID actorId, Role actorRole, UUID companyId, AuditAction action,
                        UUID targetId, String targetLabel, Instant occurredAt) {
        jdbc.update("insert into audit_events (id, occurred_at, actor_id, actor_role, company_id, action, target_id, "
                        + "target_label) values (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), Timestamp.from(occurredAt), actorId, actorRole.name(), companyId,
                action.name(), targetId, targetLabel);
    }

    @Override
    public AuditPage find(String query, AuditAction action, int page, int size) {
        String actionName = action == null ? "" : action.name();
        long total = jdbc.queryForObject("select count(*)" + FROM + FILTER, Long.class,
                query, query, query, query, actionName, actionName);
        var items = jdbc.query("select e.*, u.email as actor_email, c.name as company_name" + FROM + FILTER
                        + " order by e.occurred_at desc, e.id limit ? offset ?",
                (rs, n) -> new AuditEntry(rs.getObject("id", UUID.class), rs.getTimestamp("occurred_at").toInstant(),
                        rs.getObject("actor_id", UUID.class), rs.getString("actor_email"),
                        Role.valueOf(rs.getString("actor_role")), rs.getObject("company_id", UUID.class),
                        rs.getString("company_name"), AuditAction.valueOf(rs.getString("action")),
                        rs.getObject("target_id", UUID.class), rs.getString("target_label")),
                query, query, query, query, actionName, actionName, size, (long) page * size);
        return new AuditPage(items, total, page, size);
    }
}
