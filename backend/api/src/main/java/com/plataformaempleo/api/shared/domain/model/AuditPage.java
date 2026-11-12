package com.plataformaempleo.api.shared.domain.model;

import java.util.List;

public record AuditPage(List<AuditEntry> items, long total, int page, int size) {
    public AuditPage { items = List.copyOf(items); }
}
