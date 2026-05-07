package com.github.swim_developer.ffice.provider.infrastructure.in.rest.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

@RegisterForReflection
public record TopicsResponse(List<String> topics) {
}
