package com.github.swim_developer.ffice.provider.domain.model;

import java.time.Instant;

// TODO: Add domain-specific query filter fields
public record EventQueryFilters(
        Instant startTime,
        Instant endTime,
        int startIndex,
        int count
) {
}
