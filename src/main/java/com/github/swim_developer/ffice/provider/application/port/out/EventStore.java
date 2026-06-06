package com.github.swim_developer.ffice.provider.application.port.out;

import com.github.swim_developer.ffice.provider.domain.model.StoredEvent;
import com.github.swim_developer.ffice.provider.domain.model.EventQueryFilters;
import com.github.swim_developer.framework.domain.model.EventStatus;

import java.util.List;
import java.util.Optional;

public interface EventStore {

    void persist(StoredEvent entity);

    void update(StoredEvent entity);

    StoredEvent findDomainById(String id);

    Optional<StoredEvent> findByEventId(String eventId);

    List<StoredEvent> findWithFilters(EventQueryFilters filters);

    List<StoredEvent> findByStatus(EventStatus status);

    List<StoredEvent> findByStatus(EventStatus status, int limit);

    List<StoredEvent> findPendingDelivery(int limit);

    long countByStatus(EventStatus status);

    boolean existsByEventId(String eventId);
}
