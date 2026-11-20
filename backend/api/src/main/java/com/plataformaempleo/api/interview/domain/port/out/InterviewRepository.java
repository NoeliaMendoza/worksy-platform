package com.plataformaempleo.api.interview.domain.port.out;

import com.plataformaempleo.api.interview.domain.model.Interview;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterviewRepository {
    record Item(Interview interview, String vacancyTitle, String companyName, String candidateEmail, boolean unread) { }
    record Event(int version, Interview.Change change, Instant startsAt, int durationMinutes, String zoneId, Instant changedAt) { }

    Optional<Interview> find(UUID id);
    boolean insert(Interview interview);
    boolean update(Interview interview, int expectedVersion);
    void record(Interview interview, Interview.Change change, UUID actorId);
    List<Item> list(UUID userId, UUID companyId, Interview.View view, Instant now, int offset, int limit);
    long unread(UUID userId, UUID companyId);
    void markRead(UUID interviewId, UUID userId, int version);
    List<Event> history(UUID interviewId);
}
