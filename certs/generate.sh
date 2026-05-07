#!/bin/bash
# Generates local development certificates for swim-ffice-provider.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CERTS_DIR="${SCRIPT_DIR}"
TMP_DIR="${CERTS_DIR}/.tmp"
PASSWORD="changeit"

BROKER_SANS=(
    "localhost" "127.0.0.1" "::1"
    "ffice-provider-artemis" "provider-artemis"
    "artemis.127.0.0.1.nip.io"
    "ffice-provider-artemis.127.0.0.1.nip.io"
    "ffice-provider-artemis.swim.lab"
    "provider-artemis.swim.lab"
)

KEYCLOAK_SANS=(
    "keycloak.swim.lab" "keycloak"
    "localhost" "127.0.0.1" "::1"
    "keycloak.127.0.0.1.nip.io"
)

PROVIDER_SANS=(
    "localhost" "127.0.0.1" "::1"
    "ffice-provider"
    "provider.127.0.0.1.nip.io"
    "ffice-provider.127.0.0.1.nip.io"
    "ffice-provider.swim.lab"
)

CLIENT_SANS=(
    "ffice-provider" "localhost" "127.0.0.1"
    "ffice-provider.127.0.0.1.nip.io"
    "ffice-provider.swim.lab"
)

VALIDATOR_SANS=(
    "ffice-provider-validator" "localhost" "127.0.0.1"
    "ffice-provider-validator.127.0.0.1.nip.io"
    "ffice-provider-validator.swim.lab"
)

for f in ca.crt tls.crt tls.key client.crt client.key broker.p12 ca-truststore.p12 \
          keycloak-keystore.p12 validator-keystore.p12 validator-truststore.p12; do
    rm -f "${CERTS_DIR}/${f}"
done

mkcert -install
mkdir -p "${TMP_DIR}"
trap 'rm -rf "${TMP_DIR}"' EXIT

CA_ROOT="$(mkcert -CAROOT)"
cp "${CA_ROOT}/rootCA.pem" "${CERTS_DIR}/ca.crt"

# Artemis broker certificate
mkcert -cert-file "${TMP_DIR}/broker.crt" -key-file "${TMP_DIR}/broker.key" "${BROKER_SANS[@]}"
openssl pkcs12 -export \
    -in "${TMP_DIR}/broker.crt" -inkey "${TMP_DIR}/broker.key" \
    -certfile "${CERTS_DIR}/ca.crt" -out "${CERTS_DIR}/broker.p12" \
    -name broker -password "pass:${PASSWORD}"
chmod 644 "${CERTS_DIR}/broker.p12"

# Artemis CA truststore
keytool -importcert -noprompt -alias swim-ca -file "${CERTS_DIR}/ca.crt" \
    -keystore "${CERTS_DIR}/ca-truststore.p12" -storetype PKCS12 -storepass "${PASSWORD}"
chmod 644 "${CERTS_DIR}/ca-truststore.p12"

# Provider HTTPS certificate
mkcert -cert-file "${CERTS_DIR}/tls.crt" -key-file "${CERTS_DIR}/tls.key" "${PROVIDER_SANS[@]}"
chmod 644 "${CERTS_DIR}/tls.crt" "${CERTS_DIR}/tls.key"

# Keycloak HTTPS certificate
mkcert -cert-file "${TMP_DIR}/keycloak.crt" -key-file "${TMP_DIR}/keycloak.key" "${KEYCLOAK_SANS[@]}"
openssl pkcs12 -export \
    -in "${TMP_DIR}/keycloak.crt" -inkey "${TMP_DIR}/keycloak.key" \
    -certfile "${CERTS_DIR}/ca.crt" -out "${CERTS_DIR}/keycloak-keystore.p12" \
    -name keycloak -password "pass:${PASSWORD}"
chmod 644 "${CERTS_DIR}/keycloak-keystore.p12"

# Provider AMQP client certificate
mkcert -client -cert-file "${CERTS_DIR}/client.crt" -key-file "${CERTS_DIR}/client.key" "${CLIENT_SANS[@]}"
chmod 644 "${CERTS_DIR}/client.crt" "${CERTS_DIR}/client.key"

# Validator client certificate and keystores
mkcert -client \
    -cert-file "${TMP_DIR}/validator-client.crt" \
    -key-file  "${TMP_DIR}/validator-client.key" \
    "${VALIDATOR_SANS[@]}"
openssl pkcs12 -export \
    -in "${TMP_DIR}/validator-client.crt" -inkey "${TMP_DIR}/validator-client.key" \
    -certfile "${CERTS_DIR}/ca.crt" -out "${CERTS_DIR}/validator-keystore.p12" \
    -name validator -password "pass:${PASSWORD}"
chmod 644 "${CERTS_DIR}/validator-keystore.p12"
keytool -importcert -noprompt -alias swim-ca -file "${CERTS_DIR}/ca.crt" \
    -keystore "${CERTS_DIR}/validator-truststore.p12" -storetype PKCS12 -storepass "${PASSWORD}"
chmod 644 "${CERTS_DIR}/validator-truststore.p12"

echo "Done. All certificates generated in certs/"
