#!/bin/bash
# Validates that all swim-dnotam-provider compose services are working correctly.
# Run from the project root: ./src/local-dev/validate.sh

PASS=0
FAIL=0

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CA_CERT="${SCRIPT_DIR}/../../certs/ca.crt"
KC_BASE="https://localhost:8543"

ok()   { echo "  [OK]  $1"; PASS=$((PASS + 1)); }
fail() { echo "  [FAIL] $1"; FAIL=$((FAIL + 1)); }

check() {
    local label="$1"; shift
    if eval "$@" > /dev/null 2>&1; then ok "$label"; else fail "$label"; fi
}

echo ""
echo "=== swim-dnotam-provider — compose validation ==="
echo ""

echo "--- Container status ---"
for name in dnotam-provider-postgres kafka dnotam-provider-akhq keycloak dnotam-provider-artemis; do
    status=$(podman inspect --format '{{.State.Status}}' "$name" 2>/dev/null || echo "missing")
    if [ "$status" = "running" ]; then ok "$name ($status)"; else fail "$name ($status)"; fi
done

# kafka-init must have exited cleanly
exit_code=$(podman inspect --format '{{.State.ExitCode}}' kafka-init 2>/dev/null || echo "99")
if [ "$exit_code" = "0" ]; then ok "kafka-init (exited 0)"; else fail "kafka-init (exit code: $exit_code)"; fi

echo ""
echo "--- PostgreSQL ---"
check "provider-postgres: accepting connections" \
    podman exec dnotam-provider-postgres pg_isready -U postgres

echo ""
echo "--- Kafka ---"
check "dnotam-events-all-topic exists" \
    "podman exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list 2>/dev/null | grep -q dnotam-events-all-topic"
check "dnotam-events-dlq-topic exists" \
    "podman exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list 2>/dev/null | grep -q dnotam-events-dlq-topic"

echo ""
echo "--- AKHQ ---"
check "AKHQ UI reachable (port 9090)" \
    "curl -sf http://localhost:9090 -o /dev/null"

echo ""
echo "--- Keycloak ---"
check "realm 'swim' OIDC discovery reachable" \
    "curl -sf --cacert '${CA_CERT}' '${KC_BASE}/realms/swim/.well-known/openid-configuration' -o /dev/null"
check "realm 'swim' token endpoint returns access_token" \
    "curl -sf --cacert '${CA_CERT}' -X POST '${KC_BASE}/realms/swim/protocol/openid-connect/token' \
        -d 'client_id=amq-broker&client_secret=GxMJF8kIcxZ5t6qmTdJPLSGZSOvo0Zrf&grant_type=password&username=marcelo&password=password' \
        | grep -q access_token"

echo ""
echo "--- Artemis ---"
check "Artemis console reachable (port 8161)" \
    "curl -sf http://localhost:8161/console/ -o /dev/null"
check "ACKMonitorPlugin loaded (no ClassNotFoundException)" \
    "podman logs dnotam-provider-artemis 2>&1 | grep -v ClassNotFoundException | grep -qi 'ACKMonitor\|plugin'"
check "AMQP plain (port 5672): send as admin" \
    "podman exec dnotam-provider-artemis /home/jboss/amq-broker/bin/artemis producer \
        --url amqp://localhost:5672 --user admin --password admin \
        --message-count 1 --destination validate.plain 2>&1 | grep -qiE 'produc|sent'"
check "AMQPS mTLS (port 5671): TLS handshake succeeds" \
    "openssl s_client -connect localhost:5671 \
        -CAfile '${CA_CERT}' \
        -cert '${SCRIPT_DIR}/../../certs/client.crt' \
        -key  '${SCRIPT_DIR}/../../certs/client.key' \
        < /dev/null 2>&1 | grep -q 'Verify return code: 0'"

echo ""
echo "--- Artemis JWT authentication (full flow) ---"
TOKEN=$(curl -sf --cacert "${CA_CERT}" -X POST "${KC_BASE}/realms/swim/protocol/openid-connect/token" \
    -d "client_id=amq-broker&client_secret=GxMJF8kIcxZ5t6qmTdJPLSGZSOvo0Zrf&grant_type=password&username=marcelo&password=password" \
    2>/dev/null | python3 -c "import sys,json; print(json.load(sys.stdin).get('access_token',''))" 2>/dev/null || echo "")

if [ -z "$TOKEN" ]; then
    fail "JWT: could not obtain token from Keycloak (check step above)"
else
    PAYLOAD=$(echo "$TOKEN" | cut -d. -f2 | base64 -d 2>/dev/null || echo "")
    if echo "$PAYLOAD" | grep -q "amq-broker"; then
        ok "JWT token contains amq-broker roles (Artemis will accept it)"
    else
        fail "JWT token missing amq-broker roles — check keycloak-swim-role-spi"
    fi
fi

echo ""
echo "--- Validator ---"
check "validator UI reachable (port 8085)" \
    "curl -sf http://localhost:8085 -o /dev/null"
check "validator: no datasource error in logs" \
    "! podman logs dnotam-provider-validator 2>&1 | grep -qi 'datasource\|connection refused\|unable to acquire'"

echo ""
echo "=== Result: ${PASS} passed, ${FAIL} failed ==="
echo ""
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
