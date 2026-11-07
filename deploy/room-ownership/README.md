# Local room ownership fixture

Run from the repository root with PowerShell 7 and Docker Engine 28 or newer.
The example generates a fresh Compose project and synthetic credentials for two
verified accounts. Use the same shell for startup, checks and cleanup.

```powershell
$pilotProject = 'room-pilot-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
$pilotCompose = @('-p', $pilotProject, '-f', 'deploy/room-ownership/compose.local.yaml')
$env:FASTTOWIN_ROOM_OWNERSHIP_DATABASE_PASSWORD = [guid]::NewGuid().ToString('N')
$env:FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_PASSWORD = 'Pilot1!' + [guid]::NewGuid().ToString('N')
$env:FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_ID = [guid]::NewGuid().ToString()
$env:FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS = @([guid]::NewGuid(), [guid]::NewGuid()) -join ','
$env:FASTTOWIN_ROOM_OWNERSHIP_PEER_SECRET = [Convert]::ToBase64String(
    [System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
)

.\gradlew.bat :server:installDist
docker compose @pilotCompose config --quiet
docker compose @pilotCompose up --build --wait --wait-timeout 120
$pilotUrl = 'http://' + (docker compose @pilotCompose port caddy 8080).Trim()
```

The seed service migrates PostgreSQL and creates `room-pilot-1@example.test`
and `room-pilot-2@example.test` using the generated fixture password. Both
backends start after the seed service exits successfully. Caddy routes game,
auth, API and health traffic across upstreams that pass its `/ready` probe.

```powershell
docker compose @pilotCompose ps
curl.exe -fsS "$pilotUrl/health"
docker compose @pilotCompose exec -T caddy wget -qO- http://backend-a:8080/ready
docker compose @pilotCompose exec -T caddy wget -qO- http://backend-b:8080/ready
foreach ($path in @('/ready', '/internal', '/internal/metrics')) {
    curl.exe -sS -o NUL -w "%{http_code}`n" "$pilotUrl$path"
}
```

Expect a successful liveness response, successful private readiness on both
backends and three public `404` responses. In `ps`, Caddy is the sole service
with a host port binding. Its loopback port is assigned by Docker unless
`FASTTOWIN_ROOM_OWNERSHIP_PORT` is set. For concurrent runs or a subnet conflict,
set `FASTTOWIN_ROOM_OWNERSHIP_SUBNET_PREFIX` to the first three octets of an
unused RFC 1918 IPv4 subnet before creating the project. The default prefix is
`172.31.231`.

Remove the fixture and its data volumes using the same project name.

```powershell
docker compose @pilotCompose down --volumes --rmi local
```
