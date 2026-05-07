package com.github.swim_developer.ffice.provider.infrastructure.out.xml;

import com.github.swim_developer.ffice.provider.application.port.out.MessageAssemblerPort;
import com.github.swim_developer.ffice.provider.domain.model.StoredEvent;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

// TODO: Implement XML message assembly for WFS GetFeature responses
@ApplicationScoped
@Slf4j
public class MessageAssembler implements MessageAssemblerPort {

    @Override
    public String assemble(List<StoredEvent> events) {
        if (events.isEmpty()) {
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><empty/>";
        }

        // TODO: Build proper XML response aggregating events
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<events>\n");
        for (StoredEvent event : events) {
            sb.append(event.getXmlMessage()).append("\n");
        }
        sb.append("</events>");
        return sb.toString();
    }
}
