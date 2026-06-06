package com.github.swim_developer.ffice.provider.infrastructure.out.xml;

import aero.fixm.ffice.FficeMessageType;
import aero.fixm.ffice.validation.FficeUnmarshallerPool;
import com.github.swim_developer.framework.application.port.out.SwimXmlUnmarshallerPort;
import com.github.swim_developer.framework.domain.exception.XmlValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class JaxbUnmarshallerPool implements SwimXmlUnmarshallerPort<FficeMessageType> {

    private final FficeUnmarshallerPool pool;

    public JaxbUnmarshallerPool() {
        this.pool = new FficeUnmarshallerPool();
        log.info("FF-ICE JAXB unmarshaller pool initialized from fixm-ffice-model");
    }

    @Override
    public FficeMessageType unmarshalAndValidate(String xml) throws XmlValidationException {
        try {
            Object result = pool.unmarshalAndValidate(xml);
            if (result instanceof FficeMessageType message) {
                return message;
            }
            throw new XmlValidationException("Unexpected root type from FF-ICE unmarshaller: "
                    + (result != null ? result.getClass().getName() : "null"));
        } catch (FficeUnmarshallerPool.FficeUnmarshalException e) {
            throw new XmlValidationException(e.getMessage(), e);
        }
    }
}
