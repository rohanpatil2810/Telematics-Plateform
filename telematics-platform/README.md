# Telematics Alert Engine

Spring Boot 4 / Java 17 backend for packet ingestion and the OVERSPEED, FATIGUE, and DEVICE_OFFLINE alert rules in the interview assignment.

## Project structure

The base package is `in.rohanIT`:

```text
controller/                  REST endpoints and API error handling
dto/                         Request and response data shapes
interfaces/                  AlertRule, state repository, and zone resolver contracts
model/                       Packet, Alert, DeviceState, and enums
service/                     Packet orchestration, validation, zone logic, alert services
config/                       Alert thresholds, scheduling, and clock configuration
```

The controller calls `PacketIngestionService`; the service applies each `AlertRule` implementation to live packets. Add a rule by implementing `AlertRule`, returning a unique `AlertType`, and registering it as a Spring component.

## Run it

From this directory, in PowerShell:

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=test"
```

The test profile provides the adjustable clock endpoint used for repeatable demos. Do not enable that profile in production. Without it, the service uses the UTC system clock and does not register `/api/v1/test/clock`.

The service listens on `http://localhost:8080`. State is in memory and is cleared when the application restarts.

### Run from STS / Eclipse

Import the folder as **Existing Maven Project**, wait for Maven dependency resolution, then open **Run Configurations → Spring Boot App → Arguments** and add this program argument:

```text
--spring.profiles.active=test
```

Run `TelematicsPlatformApplication`. If the console reports that port 8080 is already in use, stop the other application using that port or change `server.port` for this run and update the Postman environment's `baseUrl` to match. A 404 from `/api/v1/test/clock` means the app started without the `test` profile; the endpoint is intentionally disabled outside that profile.

## API

### Set demo clock (test profile only)

`PUT http://localhost:8080/api/v1/test/clock`

```json
{
  "now": "2026-10-26T09:00:00Z"
}
```

This sets the injected clock and immediately checks device-offline alerts.

### Ingest a batch

`POST http://localhost:8080/api/v1/packets`

```json
[
  {
    "deviceId": "POSTMAN-DEMO",
    "timestamp": "2026-10-26T09:00:00Z",
    "lat": 23.01,
    "lon": 72.555,
    "speedKmh": 50,
    "ignition": true
  }
]
```

The response reports accepted, duplicate, out-of-order-ignored, and rejected counts. A rejected packet includes its zero-based index and reason. A repeated `(deviceId, timestamp)` is ignored. Packets for one device in the same request are processed chronologically; an unseen packet arriving in a later request with an older timestamp is accepted but ignored by the live rules. No packet history is retained for replay or recalculation.

### List alerts

`GET http://localhost:8080/api/v1/alerts`

Optional query filters can be combined:

- `GET /api/v1/alerts?deviceId=POSTMAN-DEMO`
- `GET /api/v1/alerts?type=OVERSPEED`
- `GET /api/v1/alerts?deviceId=POSTMAN-DEMO&type=OVERSPEED`

Alert types are the enum values `OVERSPEED`, `FATIGUE`, and `DEVICE_OFFLINE`.

### Swagger / OpenAPI

With the application running, open Swagger UI at `http://localhost:8080/swagger-ui/index.html`. The OpenAPI document is available at `http://localhost:8080/v3/api-docs`. Swagger lists the packet ingestion and alert listing endpoints; the test-clock endpoint appears only with the `test` profile.

## cURL examples (Windows PowerShell)

Use `curl.exe` in PowerShell (`curl` alone may be an alias). PowerShell removes embedded JSON quotes when it passes arguments to native programs, so escape the quotes as shown. Start the server with the test profile command above, then:

Set deterministic time:

```powershell
$clockBody = '{"now":"2026-10-26T09:00:00Z"}'
$escapedClockBody = $clockBody.Replace('"','\"')
curl.exe -X PUT "http://localhost:8080/api/v1/test/clock" `
  -H "Content-Type: application/json" `
  --data-raw $escapedClockBody
```

Ingest a continuous overspeed run in the overlapping school/highway area:

```powershell
$packetBody = '[{"deviceId":"POSTMAN-DEMO","timestamp":"2026-10-26T09:00:00Z","lat":23.01,"lon":72.555,"speedKmh":50,"ignition":true},{"deviceId":"POSTMAN-DEMO","timestamp":"2026-10-26T09:00:15Z","lat":23.01,"lon":72.555,"speedKmh":55,"ignition":true},{"deviceId":"POSTMAN-DEMO","timestamp":"2026-10-26T09:00:30Z","lat":23.01,"lon":72.555,"speedKmh":60,"ignition":true}]'
$escapedPacketBody = $packetBody.Replace('"','\"')
curl.exe -X POST "http://localhost:8080/api/v1/packets" `
  -H "Content-Type: application/json" `
  --data-raw $escapedPacketBody
```

Expected ingestion counts: `acceptedCount: 3`, `duplicateCount: 0`, `rejectedCount: 0`.

Query the generated alert:

```powershell
curl.exe -G "http://localhost:8080/api/v1/alerts" `
  --data-urlencode "deviceId=POSTMAN-DEMO" `
  --data-urlencode "type=OVERSPEED"
```

Expected: one `OPEN` alert with `startTime` `2026-10-26T09:00:00Z`, `worstZoneId` `SCHOOL-DPS`, and limit 30 km/h.

Check idempotency and partial acceptance:

```powershell
$partialBody = '[{"deviceId":"POSTMAN-CHECK","timestamp":"2026-10-26T09:00:00Z","lat":23,"lon":72,"speedKmh":20,"ignition":true},{"deviceId":"POSTMAN-CHECK","timestamp":"2026-10-26T09:00:00Z","lat":23,"lon":72,"speedKmh":20,"ignition":true},{"deviceId":"POSTMAN-BAD","timestamp":"2026-10-26T09:00:00Z","lat":91,"lon":72,"speedKmh":20,"ignition":true}]'
$escapedPartialBody = $partialBody.Replace('"','\"')
curl.exe -X POST "http://localhost:8080/api/v1/packets" `
  -H "Content-Type: application/json" `
  --data-raw $escapedPartialBody
```

Expected counts: accepted `1`, duplicate `1`, rejected `1`; the rejection identifies the invalid latitude.

Trigger the fatigue rule with two packets spanning exactly 4 hours 30 minutes of driving:

```powershell
$fatigueClockBody = '{"now":"2026-10-26T06:31:00Z"}'
$escapedFatigueClockBody = $fatigueClockBody.Replace('"','\"')
curl.exe -X PUT "http://localhost:8080/api/v1/test/clock" `
  -H "Content-Type: application/json" `
  --data-raw $escapedFatigueClockBody

$fatiguePacketBody = '[{"deviceId":"CURL-FATIGUE","timestamp":"2026-10-26T02:00:00Z","lat":23.03,"lon":72.505,"speedKmh":60,"ignition":true},{"deviceId":"CURL-FATIGUE","timestamp":"2026-10-26T06:30:00Z","lat":23.03,"lon":72.505,"speedKmh":60,"ignition":true}]'
$escapedFatiguePacketBody = $fatiguePacketBody.Replace('"','\"')
curl.exe -X POST "http://localhost:8080/api/v1/packets" `
  -H "Content-Type: application/json" `
  --data-raw $escapedFatiguePacketBody

curl.exe -G "http://localhost:8080/api/v1/alerts" `
  --data-urlencode "deviceId=CURL-FATIGUE" `
  --data-urlencode "type=FATIGUE"
```

Expected: an `OPEN` FATIGUE alert starting at `2026-10-26T06:30:00Z`.

Trigger and then confirm an offline alert:

```powershell
$offlineClockBody = '{"now":"2026-10-27T10:00:00Z"}'
$escapedOfflineClockBody = $offlineClockBody.Replace('"','\"')
curl.exe -X PUT "http://localhost:8080/api/v1/test/clock" `
  -H "Content-Type: application/json" `
  --data-raw $escapedOfflineClockBody

$offlinePacketBody = '[{"deviceId":"CURL-OFFLINE","timestamp":"2026-10-27T10:00:00Z","lat":23.03,"lon":72.505,"speedKmh":40,"ignition":true}]'
$escapedOfflinePacketBody = $offlinePacketBody.Replace('"','\"')
curl.exe -X POST "http://localhost:8080/api/v1/packets" `
  -H "Content-Type: application/json" `
  --data-raw $escapedOfflinePacketBody

$offlineCheckClockBody = '{"now":"2026-10-27T10:15:00Z"}'
$escapedOfflineCheckClockBody = $offlineCheckClockBody.Replace('"','\"')
curl.exe -X PUT "http://localhost:8080/api/v1/test/clock" `
  -H "Content-Type: application/json" `
  --data-raw $escapedOfflineCheckClockBody

curl.exe -G "http://localhost:8080/api/v1/alerts" `
  --data-urlencode "deviceId=CURL-OFFLINE" `
  --data-urlencode "type=DEVICE_OFFLINE"
```

Expected: an `OPEN` DEVICE_OFFLINE alert starting at `2026-10-27T10:00:00Z`. To confirm a real outage, prepare and send a packet body with `timestamp` `2026-10-27T10:15:00Z`; the alert should become `CLOSED` with that timestamp as `endTime`.

## Postman walkthrough

1. Import [Telematics Alert Engine.postman_collection.json](./postman/Telematics%20Alert%20Engine.postman_collection.json) and [Telematics Local.postman_environment.json](./postman/Telematics%20Local.postman_environment.json). Select the **Telematics Local** environment.
2. Start the server with the test profile command above.
3. Send **Set test clock**, **Ingest overspeed demo**, then **List overspeed alerts**. The GET response should show an OPEN OVERSPEED alert, with `SCHOOL-DPS` as `worstZoneId` because the school polygon overlaps the highway polygon.
4. Send **Validate partial batch and duplicate idempotency**. Expected counts: accepted `1`, duplicate `1`, rejected `1`.
5. To demonstrate fatigue, send **Set fatigue demo clock**, **Ingest fatigue demo**, and **List fatigue alerts**. Expect one OPEN alert starting at `2026-10-26T06:30:00Z`.
6. To demonstrate the offline lifecycle, send **Set offline demo start clock**, **Ingest offline demo packet**, **Advance clock and check offline**, **List open offline alert**, **Ingest recovered offline packet**, and **List closed offline alert**. The last response should show CLOSED with `endTime` `2026-10-27T10:15:00Z`.
7. To replay a supplied fixture, run one fixture mode per clean application process. Stop the app with Ctrl+C and restart it with the test profile between fixture runs. The script refuses to run when alerts are already present, and the offline mode preserves the clock, ingest, and check order from the supplied scenario:

   ```powershell
   .\postman\replay-fixtures.ps1 -Fixture overspeed
   # Stop and restart the app before the next fixture.
   .\postman\replay-fixtures.ps1 -Fixture fatigue
   # Stop and restart the app before the next fixture.
   .\postman\replay-fixtures.ps1 -Fixture offline
   ```

   Each command calls the same HTTP endpoints and prints accepted, duplicate, out-of-order, and rejected totals plus final alert records as JSON lines. It requires a fresh server process running with the test profile. Likewise, restart the server before rerunning the fixed-ID Postman demos because ingestion is intentionally idempotent and state is in memory.

Before creating a submission ZIP, run `.\mvnw.cmd clean` and exclude the generated `target\` directory. The project `.gitignore` excludes it from Git, but a manually-created ZIP may include ignored files.

For interview screenshots, capture the Postman response pane for **Set test clock**, the demo ingestion response, the filtered alert response, and the partial-batch response. Also capture the Swagger UI showing both API operations. Do not use a screenshot from before sending the requests: the alert GET must show the live service response. Save screenshots locally in the format requested by the interviewer; none are fabricated or committed here.

## Design and concurrency

- `AlertRule` separates the independently testable alert evaluators. `AlertType` and `AlertStatus` are enums, preventing inconsistent string values.
- Per-device state retains seen timestamps, the latest packet, rule progress, and the latest lifecycle alert per type—not packet history. Ingestion synchronizes on that device’s state, so simultaneous requests for one device cannot race rule updates, while unrelated devices can proceed concurrently.
- Rules are evaluated incrementally as packets arrive. Same-request packets are sorted per device; packets older than the latest processed timestamp in a later request are ignored by rule evaluation rather than triggering historical recalculation.
- Overspeed and fatigue use timestamp intervals between consecutive live packets; repeated `(deviceId, timestamp)` packets do not alter rule state.
- Device-offline checks run on a scheduled interval and immediately after test-clock updates. New packet ingestion reconciles existing offline alerts.
- Alert thresholds and time windows are centralized in `src/main/resources/application.yml`; zone polygons are in `src/main/resources/data/zones.json`.

## Production scaling discussion

The in-memory implementation is appropriate to the assignment, not a 200,000-device deployment. Production would persist packets and alert state, partition streams by device ID, use durable keyed processing with checkpointing, enforce uniqueness on `(device_id, timestamp)`, and use a distributed scheduler/lease for offline scans. API rate limits, authentication, observability, retention, and horizontal load testing would also be required.
