package com.plataformaempleo.api.identity.domain.model;

import java.util.List;

public record UserPage(List<UserOverview> items, long total, int page, int size) {
    public UserPage { items = List.copyOf(items); }
}
