package com.plataformaempleo.api.vacancy.domain.model;

import com.plataformaempleo.common.domain.DomainException;
import java.math.BigDecimal;
import java.util.Currency;

/** Condiciones públicas opcionales para mantener compatibles las vacantes anteriores. */
public record OfferDetails(ContractType contractType, WorkSchedule workSchedule, String area,
        String experienceLevel, String responsibilities, String requirements, String desirable,
        String benefits, String schedule, String remoteConditions, BigDecimal salaryMin,
        BigDecimal salaryMax, String currency, SalaryPeriod salaryPeriod) {
    public enum ContractType { INDEFINITE, FIXED_TERM, SERVICES, INTERNSHIP, OTHER }
    public enum WorkSchedule { FULL_TIME, PART_TIME, FLEXIBLE }
    public enum SalaryPeriod { HOUR, MONTH, YEAR }

    public OfferDetails {
        area = clean(area, 120);
        experienceLevel = clean(experienceLevel, 120);
        responsibilities = clean(responsibilities, 10_000);
        requirements = clean(requirements, 10_000);
        desirable = clean(desirable, 10_000);
        benefits = clean(benefits, 10_000);
        schedule = clean(schedule, 1000);
        remoteConditions = clean(remoteConditions, 2000);
        currency = clean(currency, 3);
        boolean salary = salaryMin != null || salaryMax != null || currency != null || salaryPeriod != null;
        if (salary) {
            if (salaryMin == null || salaryMax == null || currency == null || salaryPeriod == null
                    || salaryMin.signum() < 0 || salaryMax.compareTo(salaryMin) < 0
                    || salaryMin.scale() > 2 || salaryMax.scale() > 2
                    || salaryMax.compareTo(new BigDecimal("999999999999.99")) > 0) {
                throw new DomainException("vacancy.salary.invalid", "Indica un rango salarial válido, moneda y período; o deja todos esos campos vacíos");
            }
            try { Currency.getInstance(currency); }
            catch (IllegalArgumentException ex) {
                throw new DomainException("vacancy.salary.invalid", "Indica una moneda ISO válida, por ejemplo USD");
            }
        }
    }

    public void requirePublishable() {
        if (contractType == null || workSchedule == null || responsibilities == null || requirements == null) {
            throw new DomainException("vacancy.offer.required", "Completa contrato, jornada, responsabilidades y requisitos antes de publicar");
        }
    }

    private static String clean(String raw, int max) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.strip();
        if (value.codePointCount(0, value.length()) > max) {
            throw new DomainException("vacancy.offer.invalid", "Un campo de la oferta supera el límite de " + max + " caracteres");
        }
        return value;
    }
}
