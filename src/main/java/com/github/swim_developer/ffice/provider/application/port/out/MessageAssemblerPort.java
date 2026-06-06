package com.github.swim_developer.ffice.provider.application.port.out;

import com.github.swim_developer.ffice.provider.domain.model.StoredEvent;

import java.util.List;

public interface MessageAssemblerPort {

    String assemble(List<StoredEvent> events);
}
