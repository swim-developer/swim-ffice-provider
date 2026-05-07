package com.github.swim_developer.ffice.provider.infrastructure.out.persistence;

import com.github.swim_developer.ffice.provider.domain.model.FailedDelivery;
import com.github.swim_developer.ffice.provider.infrastructure.out.persistence.entity.FailedDeliveryJpaEntity;
import com.github.swim_developer.framework.application.port.out.FailedDeliveryStore;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ProviderFailedDeliveryStore
        implements PanacheRepository<FailedDeliveryJpaEntity>, FailedDeliveryStore<FailedDelivery> {

    private final ProviderPersistenceMapper mapper;

    @Inject
    public ProviderFailedDeliveryStore(ProviderPersistenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void persist(FailedDelivery domain) {
        persistAndFlush(mapper.toJpa(domain));
    }

    @Override
    public FailedDelivery createRecord(String eventId, UUID subscriptionId, String queue, String errorMessage) {
        return FailedDelivery.builder()
                .eventId(eventId).subscriptionId(subscriptionId)
                .queue(queue).errorMessage(errorMessage).build();
    }

    @Override
    public List<FailedDelivery> findPendingRetries(int maxRetries, int batchSize) {
        return find("resolved = false AND retryCount < ?1 ORDER BY createdAt ASC", maxRetries)
                .page(0, batchSize).list().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<FailedDelivery> findExceededRetries(int maxRetries, int batchSize) {
        return find("resolved = false AND retryCount >= ?1 ORDER BY createdAt ASC", maxRetries)
                .page(0, batchSize).list().stream().map(mapper::toDomain).toList();
    }

    @Override
    public long countPendingByEventId(String eventId) {
        return count("eventId = ?1 AND resolved = false", eventId);
    }
}
