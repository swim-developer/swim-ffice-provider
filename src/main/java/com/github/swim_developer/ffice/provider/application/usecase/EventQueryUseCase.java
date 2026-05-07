package com.github.swim_developer.ffice.provider.application.usecase;

import com.github.swim_developer.ffice.provider.domain.model.StoredEvent;
import com.github.swim_developer.ffice.provider.application.port.in.QueryEventPort;
import com.github.swim_developer.ffice.provider.application.port.out.MessageAssemblerPort;
import com.github.swim_developer.ffice.provider.application.port.out.EventStore;
import com.github.swim_developer.ffice.provider.domain.model.EventQueryFilters;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@Slf4j
public class EventQueryUseCase implements QueryEventPort {

    private final EventStore eventRepository;
    private final MessageAssemblerPort assembler;
    private final MeterRegistry registry;

    @Inject
    public EventQueryUseCase(EventStore eventRepository,
                             MessageAssemblerPort assembler,
                             MeterRegistry registry) {
        this.eventRepository = eventRepository;
        this.assembler = assembler;
        this.registry = registry;
    }

    public String queryFeatures(EventQueryFilters filters) {
        Timer.Sample timerSample = Timer.start(registry);
        List<StoredEvent> events = eventRepository.findWithFilters(filters);
        log.info("Query returned {} events (startIndex={}, count={})",
                events.size(), filters.startIndex(), filters.count());
        String result = assembler.assemble(events);
        timerSample.stop(Timer.builder("ffice_query_duration")
                .tag("resultCount", String.valueOf(events.size()))
                .register(registry));
        return result;
    }

    public Optional<String> findByEventId(String eventId) {
        return eventRepository.findByEventId(eventId)
                .map(StoredEvent::getXmlMessage);
    }
}
