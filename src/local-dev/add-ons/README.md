# local-dev/add-ons

Pre-built JAR files required by the local development environment. These JARs are committed to the repository so that `podman compose up` works immediately after `git clone`, with no extra build steps.

## Contents

| File | Source project | Loaded by |
|------|---------------|-----------|
| `activemq-log-plugin.jar` | [swim-developer-add-ons / activemq-log-plugins](https://github.com/swim-developer/swim-developer-add-ons) | Artemis broker (`/home/jboss/amq-broker/lib/`) |
| `keycloak-swim-role-spi.jar` | [swim-developer-add-ons / keycloak-swim-role-spi](https://github.com/swim-developer/swim-developer-add-ons) | Keycloak (`/opt/keycloak/providers/`) |

## What each add-on does

**activemq-log-plugin.jar** — Artemis broker plugin that intercepts message acknowledgments on SWIM subscription queues and publishes structured delivery audit records to an internal `ACK_MONITOR` topic. Provides broker-level proof of delivery for CP1 audit and SLA purposes.

**keycloak-swim-role-spi.jar** — Keycloak event listener SPI that automatically creates per-user AMQP client roles in the `amq-broker` Keycloak client whenever a SWIM user is registered. This is what makes JWT-based authorization scale: each subscriber's token carries the roles that grant access to their subscription queues in Artemis.

## Updating the JARs

If you need a newer version, clone [swim-developer-add-ons](https://github.com/swim-developer/swim-developer-add-ons), build the module you need, and copy the output JAR here:

```bash
# activemq-log-plugins
./mvnw clean package -DskipTests -pl activemq-log-plugins
cp activemq-log-plugins/target/activemq-log-plugin.jar \
   <path-to-this-repo>/applications/swim-dnotam-provider/src/local-dev/add-ons/activemq-log-plugin.jar

# keycloak-swim-role-spi
./mvnw clean package -DskipTests -pl keycloak-swim-role-spi
cp keycloak-swim-role-spi/target/keycloak-swim-role-spi-1.0.0-SNAPSHOT.jar \
   <path-to-this-repo>/applications/swim-dnotam-provider/src/local-dev/add-ons/keycloak-swim-role-spi.jar
```

Commit the updated JARs so all team members benefit from the newer version.
