# Validates that all swim-dnotam-provider compose services are working correctly.
# Run from the project root: .\src\local-dev\validate.ps1

$pass = 0
$fail = 0

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$caCert    = Join-Path $scriptDir "..\..\certs\ca.crt"
$kcBase    = "https://localhost:8543"

function Ok($label)   { Write-Host "  [OK]  $label" -ForegroundColor Green;  $script:pass++ }
function Fail($label) { Write-Host "  [FAIL] $label" -ForegroundColor Red;   $script:fail++ }

function Check($label, [scriptblock]$cmd) {
    try {
        $result = & $cmd 2>&1
        if ($LASTEXITCODE -eq 0) { Ok $label } else { Fail $label }
    } catch { Fail $label }
}

Write-Host ""
Write-Host "=== swim-dnotam-provider — compose validation ===" -ForegroundColor Cyan
Write-Host ""

Write-Host "--- Container status ---"
foreach ($name in @("dnotam-provider-postgres","kafka","dnotam-provider-akhq","keycloak","dnotam-provider-artemis")) {
    $status = (podman inspect --format '{{.State.Status}}' $name 2>$null) -join ""
    if ($status -eq "running") { Ok "$name ($status)" } else { Fail "$name ($status)" }
}
$exitCode = (podman inspect --format '{{.State.ExitCode}}' kafka-init 2>$null) -join ""
if ($exitCode -eq "0") { Ok "kafka-init (exited 0)" } else { Fail "kafka-init (exit code: $exitCode)" }

Write-Host ""
Write-Host "--- PostgreSQL ---"
Check "provider-postgres: accepting connections" {
    podman exec dnotam-provider-postgres pg_isready -U postgres
}

Write-Host ""
Write-Host "--- Kafka ---"
Check "dnotam-events-all-topic exists" {
    $topics = podman exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list 2>$null
    if ($topics -match "dnotam-events-all-topic") { exit 0 } else { exit 1 }
}
Check "dnotam-events-dlq-topic exists" {
    $topics = podman exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list 2>$null
    if ($topics -match "dnotam-events-dlq-topic") { exit 0 } else { exit 1 }
}

Write-Host ""
Write-Host "--- AKHQ ---"
Check "AKHQ UI reachable (port 9090)" {
    curl -sf http://localhost:9090 -o $null
}

Write-Host ""
Write-Host "--- Keycloak ---"
Check "realm 'swim' OIDC discovery reachable" {
    curl -sf --cacert $caCert "$kcBase/realms/swim/.well-known/openid-configuration" -o $null
}
Check "realm 'swim' token endpoint returns access_token" {
    $body = "client_id=amq-broker&client_secret=GxMJF8kIcxZ5t6qmTdJPLSGZSOvo0Zrf&grant_type=password&username=marcelo&password=password"
    $resp = curl -sf --cacert $caCert -X POST "$kcBase/realms/swim/protocol/openid-connect/token" -d $body 2>$null
    if ($resp -match "access_token") { exit 0 } else { exit 1 }
}

Write-Host ""
Write-Host "--- Artemis ---"
Check "Artemis console reachable (port 8161)" {
    curl -sf http://localhost:8161/console/ -o $null
}
Check "ACKMonitorPlugin loaded (no ClassNotFoundException)" {
    $logs = podman logs dnotam-provider-artemis 2>&1
    if ($logs -match "ACKMonitor|plugin" -and $logs -notmatch "ClassNotFoundException") { exit 0 } else { exit 1 }
}
Check "AMQP plain (port 5672): send as admin" {
    $out = podman exec dnotam-provider-artemis /home/jboss/amq-broker/bin/artemis producer `
        --url amqp://localhost:5672 --user admin --password admin `
        --message-count 1 --destination validate.plain 2>&1
    if ($out -match "sent") { exit 0 } else { exit 1 }
}
Check "AMQPS mTLS (port 5671): send as admin" {
    $url = "amqps://localhost:5671?sslEnabled=true&trustStorePath=/certs/ca-truststore.p12&trustStorePassword=changeit&keyStorePath=/certs/broker.p12&keyStorePassword=changeit"
    $out = podman exec dnotam-provider-artemis /home/jboss/amq-broker/bin/artemis producer `
        --url $url --user admin --password admin `
        --message-count 1 --destination validate.tls 2>&1
    if ($out -match "sent") { exit 0 } else { exit 1 }
}

Write-Host ""
Write-Host "--- Artemis JWT authentication (full flow) ---"
$tokenBody = "client_id=amq-broker&client_secret=GxMJF8kIcxZ5t6qmTdJPLSGZSOvo0Zrf&grant_type=password&username=marcelo&password=password"
$tokenResp = curl -sf --cacert $caCert -X POST "$kcBase/realms/swim/protocol/openid-connect/token" -d $tokenBody 2>$null
$token = ($tokenResp | python3 -c "import sys,json; print(json.load(sys.stdin).get('access_token',''))" 2>$null) -join ""

if ([string]::IsNullOrEmpty($token)) {
    Fail "JWT: could not obtain token from Keycloak (check step above)"
} else {
    Check "AMQPS mTLS (port 5671): send with JWT token as password" {
        $url = "amqps://localhost:5671?sslEnabled=true&trustStorePath=/certs/ca-truststore.p12&trustStorePassword=changeit"
        $out = podman exec dnotam-provider-artemis /home/jboss/amq-broker/bin/artemis producer `
            --url $url --user marcelo --password $token `
            --message-count 1 --destination validate.jwt 2>&1
        if ($out -match "sent") { exit 0 } else { exit 1 }
    }
}

Write-Host ""
Write-Host "--- Validator ---"
Check "validator UI reachable (port 8085)" {
    curl -sf http://localhost:8085 -o $null
}
Check "validator: no datasource error in logs" {
    $logs = podman logs dnotam-provider-validator 2>&1
    if ($logs -match "datasource|connection refused|unable to acquire") { exit 1 } else { exit 0 }
}

Write-Host ""
Write-Host "=== Result: $pass passed, $fail failed ===" -ForegroundColor $(if ($fail -eq 0) {"Green"} else {"Red"})
Write-Host ""
exit $(if ($fail -eq 0) {0} else {1})
