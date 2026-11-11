package com.plataformaempleo.api.company.application;

import com.plataformaempleo.api.company.domain.model.Company;
import com.plataformaempleo.api.company.domain.port.out.CompanyRepository;
import com.plataformaempleo.api.identity.domain.model.PasswordPolicy;
import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.identity.domain.model.User;
import com.plataformaempleo.api.identity.domain.port.out.PasswordHasher;
import com.plataformaempleo.api.identity.domain.port.out.UserRepository;
import com.plataformaempleo.common.domain.DomainException;
import java.time.Clock;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** Autoregistro: crea la empresa y la cuenta de su administrador/a en una sola operación. */
public class CompanyRegistrationService {

    private final CompanyRepository companies;
    private final UserRepository users;
    private final PasswordHasher hasher;
    private final Clock clock;

    public CompanyRegistrationService(CompanyRepository companies, UserRepository users, PasswordHasher hasher,
                                      Clock clock) {
        this.companies = Objects.requireNonNull(companies);
        this.users = Objects.requireNonNull(users);
        this.hasher = Objects.requireNonNull(hasher);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public Registered register(String companyName, String adminEmail, String password) {
        Company company = Company.register(companyName, clock);
        PasswordPolicy.validate(password);
        String email = User.normalizeEmail(adminEmail);
        if (users.findActiveByEmail(email).isPresent()) {
            throw new DomainException("user.email.taken", "Ya existe una cuenta con ese correo");
        }
        Company saved = companies.save(company);
        User admin = users.save(User.register(email, hasher.hash(password), Role.COMPANY_ADMIN, saved.id(), clock));
        return new Registered(saved, admin);
    }

    public record Registered(Company company, User admin) {
    }
}
