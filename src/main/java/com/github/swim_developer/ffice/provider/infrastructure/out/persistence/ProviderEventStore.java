package com.github.swim_developer.ffice.provider.infrastructure.out.persistence;

import com.github.swim_developer.ffice.provider.domain.model.StoredEvent;
import com.github.swim_developer.ffice.provider.domain.model.EventQueryFilters;
import com.github.swim_developer.ffice.provider.application.port.out.EventStore;
import com.github.swim_developer.ffice.provider.infrastructure.out.persistence.entity.EventJpaEntity;
import com.github.swim_developer.framework.domain.model.EventStatus;
import com.github.swim_developer.framework.provider.application.port.out.SwimProviderEventStorePort;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// TODO: Add domain-specific query methods
@ApplicationScoped
public class ProviderEventStore implements PanacheRepositoryBase<EventJpaEntity, String>, EventStore, SwimProviderEventStorePort {

    private final ProviderPersistenceMapper mapper;

    @Inject
    public ProviderEventStore(ProviderPersistenceMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<StoredEvent> findByEventId(String eventId) {
        return findByIdOptional(eventId).map(mapper::toDomain);
    }

    public List<StoredEvent> findWithFilters(EventQueryFilters filters) {
        StringBuilder query = new StringBuilder("1=1");
        Map<String, Object> params = new HashMap<>();
        if (filters.startTime() != null) {
            query.append(" AND receivedAt >= :startTime");
            params.put("startTime", filters.startTime());
        }
        if (filters.endTime() != null) {
            query.append(" AND receivedAt <= :endTime");
            params.put("endTime", filters.endTime());
        }
        query.append(" ORDER BY receivedAt DESC");
        int page = (filters.count() > 0) ? filters.startIndex() / filters.count() : 0;
        return find(query.toString(), params).page(page, filters.count()).list()
                .stream().map(mapper::toDomain).toList();
    }

    public List<StoredEvent> findByStatus(EventStatus status) {
        return list("status", status).stream().map(mapper::toDomain).toList();
    }

    public List<StoredEvent> findByStatus(EventStatus status, int limit) {
        return find("status = ?1 ORDER BY receivedAt ASC", status).page(0, limit).list()
                .stream().map(mapper::toDomain).toList();
    }

    public List<StoredEvent> findPendingDelivery(int limit) {
        return find("status IN (?1, ?2) ORDER BY receivedAt ASC",
                EventStatus.RECEIVED, EventStatus.PARTIALLY_DELIVERED)
                .page(0, limit).list().stream().map(mapper::toDomain).toList();
    }

    public long countByStatus(EventStatus status) {
        return count("status", status);
    }

    public boolean existsByEventId(String eventId) {
        return count("eventId", eventId) > 0;
    }

    @Override
    public void persist(StoredEvent domain) {
        persistAndFlush(mapper.toJpa(domain));
    }

    @Override
    public void update(StoredEvent domain) {
        getEntityManager().merge(mapper.toJpa(domain));
    }

    @Override
    public StoredEvent findDomainById(String id) {
        return findByIdOptional(id).map(mapper::toDomain).orElse(null);
    }

    public StoredEvent mergeDomainEntity(StoredEvent domain) {
        EventJpaEntity merged = getEntityManager().merge(mapper.toJpa(domain));
        return mapper.toDomain(merged);
    }
}
