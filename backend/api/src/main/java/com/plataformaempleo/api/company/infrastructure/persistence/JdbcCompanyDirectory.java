package com.plataformaempleo.api.company.infrastructure.persistence;

import com.plataformaempleo.api.company.domain.model.CompanyOverview;
import com.plataformaempleo.api.company.domain.model.CompanyPage;
import com.plataformaempleo.api.company.domain.port.out.CompanyDirectory;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCompanyDirectory implements CompanyDirectory {
    private static final String FILTER = " where (? = '' or strpos(lower(c.name), ?) > 0) ";
    private final JdbcTemplate jdbc;

    JdbcCompanyDirectory(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public CompanyPage find(String query, int page, int size) {
        long total = jdbc.queryForObject("select count(*) from companies c" + FILTER, Long.class, query, query);
        var items = jdbc.query("with selected as (select c.id, c.name, c.created_at from companies c" + FILTER
                + " order by c.created_at desc, c.id limit ? offset ?) " + """
                select c.id, c.name, c.created_at, team.administrators, team.recruiters,
                       jobs.vacancies, jobs.published_vacancies
                from selected c
                left join lateral (
                    select count(*) filter (where u.role = 'COMPANY_ADMIN') as administrators,
                           count(*) filter (where u.role = 'RECRUITER') as recruiters
                    from users u where u.company_id = c.id and u.deleted_at is null
                ) team on true
                left join lateral (
                    select count(*) as vacancies,
                           count(*) filter (where v.status = 'PUBLISHED') as published_vacancies
                    from vacancies v where v.company_id = c.id
                ) jobs on true
                order by c.created_at desc, c.id
                """,
                (rs, n) -> new CompanyOverview(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getTimestamp("created_at").toInstant(), rs.getLong("administrators"),
                        rs.getLong("recruiters"), rs.getLong("vacancies"), rs.getLong("published_vacancies")),
                query, query, size, (long) page * size);
        return new CompanyPage(items, total, page, size);
    }
}
