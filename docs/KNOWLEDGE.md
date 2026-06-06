# swim-ffice-provider — Knowledge Base


## What This Is

**ATFM/AISP role.** Publishes FF-ICE (Flight and Flow Information for a Collaborative Environment) messages to downstream ANSP consumers via AMQP 1.0. Data model: FIXM FF-ICE (from `swim-fixm-ffice-model`). Same framework and REST API shape as DNOTAM and ED-254 providers.

## Architecture

Same hexagonal structure as all providers. Package root: `com.github.swim_developer.ffice.provider`.

REST API shape: `/swim/v1/subscriptions`, `/swim/v1/topics`, `/swim/v1/features` — same as other providers.

All queue provisioning, heartbeat, subscription expiry, and observability are inherited from `swim-developer-framework`.

## Build & Run

```bash
cd ../swim-developer-framework && mvn clean install -DskipTests
./mvnw clean package -DskipTests
quarkus dev
./mvnw verify -DskipITs=false
```
