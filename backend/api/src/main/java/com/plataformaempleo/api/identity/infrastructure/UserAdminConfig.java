package com.plataformaempleo.api.identity.infrastructure;

import com.plataformaempleo.api.identity.application.UserAdminService;
import com.plataformaempleo.api.identity.domain.port.out.UserDirectory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class UserAdminConfig {
    @Bean
    UserAdminService userAdminService(UserDirectory users) { return new UserAdminService(users); }
}
