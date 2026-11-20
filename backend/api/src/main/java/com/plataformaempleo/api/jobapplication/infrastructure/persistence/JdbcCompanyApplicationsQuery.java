package com.plataformaempleo.api.jobapplication.infrastructure.persistence;

import com.plataformaempleo.api.jobapplication.domain.model.ApplicationStage;
import com.plataformaempleo.api.jobapplication.domain.model.CompanyApplication;
import com.plataformaempleo.api.jobapplication.domain.port.out.CompanyApplicationsQuery;
import com.plataformaempleo.api.screening.domain.model.ScreeningRequest;
import com.plataformaempleo.api.vacancy.domain.model.Vacancy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Lectura de las postulaciones de una empresa en una sola consulta.
 * Los datos personales, la etapa, el análisis y la próxima entrevista solo salen si la persona comparte
 * sus datos ({@code candidate_profiles.consent_share}); el filtro se aplica en SQL, no después.
 */
@Repository
class JdbcCompanyApplicationsQuery implements CompanyApplicationsQuery {

    /** Minúsculas y sin acentos, igual que {@link #fold(String)} hace en Java. */
    private static final String FOLD = "translate(lower(%s), 'áàäâéèëêíìïîóòöôúùüûñç', 'aaaaeeeeiiiioooouuuunc')";

    private static final String SELECT = """
            select a.id, a.vacancy_id, v.title as vacancy_title, v.status as vacancy_status, a.applied_at,
                   coalesce(p.consent_share, false) as shared,
                   case when p.consent_share then p.full_name end as candidate_name,
                   case when p.consent_share then u.email end as candidate_email,
                   case when p.consent_share then a.cv_id end as cv_id,
                   case when p.consent_share then a.stage end as stage,
                   case when p.consent_share then s.status end as screening_status,
                   case when p.consent_share then s.failure_code end as failure_code,
                   case when p.consent_share then (
                       select min(i.starts_at) from interviews i
                        where i.application_id = a.id and i.status in ('INVITED', 'CONFIRMED') and i.starts_at >= :now
                   ) end as next_interview_at
            from applications a
            join vacancies v on v.id = a.vacancy_id
            join users u on u.id = a.candidate_id
            left join candidate_profiles p on p.user_id = a.candidate_id
            left join screening_requests s on s.application_id = a.id and s.provider = :provider
            where v.company_id = :company
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final String provider;
    private final Clock clock;

    JdbcCompanyApplicationsQuery(NamedParameterJdbcTemplate jdbc,
                                 @Value("${worksy.screening.provider:baseline}") String provider, Clock clock) {
        this.jdbc = jdbc;
        this.provider = provider;
        this.clock = clock;
    }

    @Override
    public CompanyApplication.Page search(UUID companyId, CompanyApplication.Filter filter, int page, int size) {
        MapSqlParameterSource params = base(companyId);
        StringBuilder where = new StringBuilder();
        if (filter.vacancyId() != null) {
            where.append(" and a.vacancy_id = :vacancy");
            params.addValue("vacancy", filter.vacancyId());
        }
        if (filter.stage() != null) {
            // Sin permiso la etapa no se ve, así que tampoco sirve para filtrar.
            where.append(" and p.consent_share and a.stage = :stage");
            params.addValue("stage", filter.stage().name());
        }
        if (filter.text() != null) {
            where.append(" and (").append(FOLD.formatted("v.title")).append(" like :text escape '\\'")
                    .append(" or (p.consent_share and (").append(FOLD.formatted("u.email")).append(" like :text escape '\\'")
                    .append(" or ").append(FOLD.formatted("coalesce(p.full_name, '')")).append(" like :text escape '\\')))");
            params.addValue("text", "%" + escapeLike(fold(filter.text())) + "%");
        }
        Long total = jdbc.queryForObject("select count(*) from (" + SELECT + where + ") t", params, Long.class);
        params.addValue("limit", size).addValue("offset", (long) page * size);
        List<CompanyApplication> items = jdbc.query(
                SELECT + where + " order by a.applied_at desc, a.id limit :limit offset :offset", params,
                JdbcCompanyApplicationsQuery::row);
        return new CompanyApplication.Page(items, total == null ? 0 : total, page, size);
    }

    @Override
    public Optional<CompanyApplication> find(UUID companyId, UUID applicationId) {
        MapSqlParameterSource params = base(companyId).addValue("application", applicationId);
        return jdbc.query(SELECT + " and a.id = :application", params, JdbcCompanyApplicationsQuery::row)
                .stream().findFirst();
    }

    private MapSqlParameterSource base(UUID companyId) {
        return new MapSqlParameterSource()
                .addValue("company", companyId)
                .addValue("provider", provider)
                .addValue("now", Timestamp.from(clock.instant()));
    }

    static String fold(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        // "ñ" y "ç" se descomponen en n/c + marca: ya quedaron como en el translate de SQL.
        return decomposed;
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static CompanyApplication row(ResultSet rs, int n) throws SQLException {
        String stage = rs.getString("stage");
        String screening = rs.getString("screening_status");
        Timestamp interview = rs.getTimestamp("next_interview_at");
        return new CompanyApplication(
                rs.getObject("id", UUID.class),
                rs.getObject("vacancy_id", UUID.class),
                rs.getString("vacancy_title"),
                Vacancy.Status.valueOf(rs.getString("vacancy_status")),
                rs.getTimestamp("applied_at").toInstant(),
                rs.getBoolean("shared"),
                rs.getString("candidate_name"),
                rs.getString("candidate_email"),
                rs.getObject("cv_id", UUID.class),
                stage == null ? null : ApplicationStage.valueOf(stage),
                screening == null ? null : ScreeningRequest.Status.valueOf(screening),
                rs.getString("failure_code"),
                interview == null ? null : interview.toInstant());
    }
}
