package com.github.swim_developer.ffice.provider.domain.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record FilterableEvent(
        String messageId,
        String gufi,
        String messageType,
        String payload
) {
}
