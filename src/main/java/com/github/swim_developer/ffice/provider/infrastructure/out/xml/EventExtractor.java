package com.github.swim_developer.ffice.provider.infrastructure.out.xml;

import aero.fixm.ffice.FficeMessageType;
import aero.fixm.ffice.MessageTypeType;
import aero.fixm.base.UniversallyUniqueIdentifierType;
import aero.fixm.flight.FlightIdentificationType;
import aero.fixm.flight.FlightType;
import com.github.swim_developer.ffice.provider.domain.model.FilterableEvent;
import com.github.swim_developer.framework.application.port.out.SwimEventExtractor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.xml.bind.JAXBElement;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class EventExtractor implements SwimEventExtractor<FilterableEvent, FficeMessageType> {

    private static final String UNKNOWN = "unknown";

    @Override
    public List<Optional<FilterableEvent>> extract(FficeMessageType message) {
        if (message == null) {
            return List.of(Optional.empty());
        }

        try {
            String messageId = extractMessageId(message);
            String gufi = extractGufi(message);
            String messageType = extractMessageType(message);

            FilterableEvent event = new FilterableEvent(messageId, gufi, messageType, null);
            return List.of(Optional.of(event));
        } catch (RuntimeException e) {
            log.error("Failed to extract FF-ICE event metadata from message", e);
            return List.of(Optional.empty());
        }
    }

    private String extractMessageId(FficeMessageType message) {
        UniversallyUniqueIdentifierType uid = message.getUniqueMessageIdentifier();
        if (uid != null && uid.getValue() != null && !uid.getValue().isBlank()) {
            return uid.getValue();
        }
        return UNKNOWN;
    }

    private String extractGufi(FficeMessageType message) {
        FlightType flight = message.getFlight();
        if (flight == null) {
            return UNKNOWN;
        }
        JAXBElement<FlightIdentificationType> flightIdElement = flight.getFlightIdentification();
        if (flightIdElement == null || flightIdElement.getValue() == null) {
            return UNKNOWN;
        }
        FlightIdentificationType flightId = flightIdElement.getValue();
        JAXBElement<aero.fixm.base.GloballyUniqueFlightIdentifierType> gufiElement = flightId.getGufi();
        if (gufiElement == null || gufiElement.getValue() == null) {
            return UNKNOWN;
        }
        String value = gufiElement.getValue().getValue();
        return (value != null && !value.isBlank()) ? value : UNKNOWN;
    }

    private String extractMessageType(FficeMessageType message) {
        MessageTypeType type = message.getType();
        if (type != null) {
            return type.value();
        }
        return UNKNOWN;
    }
}
