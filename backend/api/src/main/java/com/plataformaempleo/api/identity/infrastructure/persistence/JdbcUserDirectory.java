package com.plataformaempleo.api.identity.infrastructure.persistence;

import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.identity.domain.model.UserOverview;
import com.plataformaempleo.api.identity.domain.model.UserPage;
import com.plataformaempleo.api.identity.domain.port.out.UserDirectory;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcUserDirectory implements UserDirectory {
    private static final String FROM = " from users u left join companies c on c.id = u.company_id ";
    private static final String FILTER = """
            where u.deleted_at is null
              and (? = '' or strpos(lower(u.email), ?) > 0 or strpos(lower(coalesce(c.name, '')), ?) > 0)
              and (? = '' or u.role = ?)
            """;
    private final JdbcTemplate jdbc;

    JdbcUserDirectory(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public UserPage find(String query, Role role, int page, int size) {
        String roleName = role == null ? "" : role.name();
        long total = jdbc.queryForObject("select count(*)" + FROM + FILTER, Long.class,
                query, query, query, roleName, roleName);
        var items = jdbc.query("select u.id, u.email, u.role, u.company_id, c.name as company_name, u.created_at"
                        + FROM + FILTER + " order by u.created_at desc, u.id limit ? offset ?",
                (rs, n) -> new UserOverview(rs.getObject("id", UUID.class), rs.getString("email"),
                        Role.valueOf(rs.getString("role")), rs.getObject("company_id", UUID.class),
                        rs.getString("company_name"), rs.getTimestamp("created_at").toInstant()),
                query, query, query, roleName, roleName, size, (long) page * size);
        return new UserPage(items, total, page, size);
    }
}
