package com.github.swim_developer.ffice.provider.infrastructure.out.config;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

// TODO: Customize the OpenAPI definition for your service
@OpenAPIDefinition(
        info = @Info(
                title = "FF-ICE Subscription and Request Service",
                version = "1.0.0",
                description = "SWIM FF-ICE Provider Service",
                contact = @Contact(
                        name = "EUROCONTROL",
                        url = "https://eur-registry.swim.aero"
                )
        ),
        tags = {
                @Tag(name = "Subscriptions", description = "Subscription lifecycle management"),
                @Tag(name = "Topics", description = "Event scenario catalog"),
                @Tag(name = "Request Interface (WFS)")
        }
)
@SecurityScheme(
        securitySchemeName = "mTLS",
        type = SecuritySchemeType.MUTUALTLS,
        description = "Mutual TLS authentication using EACP certificates"
)
public class OpenApiConfiguration extends Application {
}
