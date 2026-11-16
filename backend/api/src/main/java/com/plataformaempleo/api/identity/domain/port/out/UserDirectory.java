package com.plataformaempleo.api.identity.domain.port.out;

import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.identity.domain.model.UserPage;

public interface UserDirectory {
    UserPage find(String query, Role role, int page, int size);
}
