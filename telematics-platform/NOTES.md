# Assignment notes

## Business decisions

- Points on polygon boundaries are inside. If polygons overlap, the lowest speed limit applies.
- Overspeed opens at 30 seconds of continuous violation and closes after 15 continuous seconds at or below the zone limit. Speeds above the limit but within the configured 5% tolerance do not open an alert.
- Overspeed detail metrics include compliant samples seen while the alert is awaiting its 15-second closing confirmation; `endTime` remains the timestamp of the first compliant sample.
- Fatigue driving is `ignition = true` and `speedKmh > 5`. Packet state applies until the next packet. Driving time accumulates across stops and resets after one 45-minute break, or a valid 15-minute then 30-minute split break.
- Offline alerts are evaluated against the injectable server clock and the latest live packet. A moving device is OPEN after 15 minutes without packets. On packet arrival, a gap of at least 15 minutes closes the alert; a short gap means buffered traffic and cancels it.
- Offline packets in each request are sorted and processed as one per-device batch. An open offline alert is reconciled against the batch's newest packet only after the whole batch has been processed, preventing an intermediate buffered packet from causing premature cancellation. A qualifying 15-minute gap still closes the alert at that first gap.
- Identical `(deviceId, timestamp)` packets are duplicates and do not rerun rules. Packets for one device in a single request are ordered by timestamp. A unique packet arriving in a later request older than the latest processed timestamp is counted as accepted and `outOfOrderIgnoredCount` is incremented, but alert rules do not replay or recalculate.
- Per recruiter clarification, this is a live-alert service, not an alert-history service. Device state retains timestamp keys for deduplication, the latest packet, rule progress, and only the latest lifecycle alert per type. There is no packet-history or alert-history endpoint.
- A cancelled offline alert uses the timestamp of the packet that demonstrated resumed traffic as `endTime`. Alert IDs are generated per alert episode and are not deterministic across runs.
- The test clock endpoint is available only in the `test` profile.

## Fixture replay

Run exactly one fixture mode per fresh test-profile application process. After each run, stop and restart the application so its in-memory state cannot contaminate the next fixture. From the project root, choose one command per fresh process:

```powershell
.\postman\replay-fixtures.ps1 -Fixture overspeed
.\postman\replay-fixtures.ps1 -Fixture fatigue
.\postman\replay-fixtures.ps1 -Fixture offline
```

The script reports per-batch and total ingestion counts plus final alerts. The offline mode follows the ordered clock, ingestion, and check steps in `offline-scenario.json`; setting the clock also triggers the required offline check.

The results below were observed in a prior combined replay on a clean in-memory process, before the fixture-specific script modes were added. Run the commands above separately to verify each fixture in isolation before presenting screenshots or updated results:

| Fixture | Accepted | Duplicates | Observed result |
|---|---:|---:|---|
| `overspeed-packets.json` | 106 | 4 | V101, V102, and V105 closed; V103 and V104 had no qualifying continuous violation |
| `fatigue-packets.json` | 1,629 | 0 | V203 closed at 07:35Z; V202 remained open from 07:15Z |
| `offline-scenario.json` | 96 | — | V301 closed 10:10–10:40Z; V302 cancelled 10:10–10:32Z after the complete buffered batch; V304 closed 10:20–10:45Z; V305 remained open from 10:10Z; parked V303 had no alert |

Observed final live alert count: **9** (3 OVERSPEED CLOSED, 1 FATIGUE CLOSED, 1 FATIGUE OPEN, 2 DEVICE_OFFLINE CLOSED, 1 DEVICE_OFFLINE CANCELLED, and 1 DEVICE_OFFLINE OPEN).

## Concurrency and design walkthrough

- `PacketController` handles HTTP mapping; `PacketIngestionService` validates, deduplicates, orders packets within each request, and coordinates the alert rules.
- `AlertRule` is the extension contract in `interfaces`. `OverspeedAlertService`, `FatigueAlertService`, and `DeviceOfflineAlertService` each own one rule.
- `DeviceStateStore` uses a `ConcurrentHashMap` so devices can be created/looked up concurrently. The ingestion and scheduled offline check synchronize on an individual `DeviceState`: simultaneous updates for the same device are serialized, while different devices remain independent.
- Each alert service keeps per-device incremental rule progress in concurrent maps. The state lock protects each device's rule updates and alert lifecycle transitions.
- The in-memory deduplication set retains every timestamp seen for a device and therefore grows without bound. For production, deduplication needs durable uniqueness on `(device_id, timestamp)` plus a retention policy aligned with the accepted replay window.
- `Clock` is injected so the scheduled offline rule and test clock are deterministic. Alert thresholds and intervals live in `application.yml`; zone polygons are in `src/main/resources/data/zones.json`.

For a larger fleet, I would persist state and deduplication keys, partition a durable event stream by device ID, and run offline checks with a distributed scheduler/lease. I would also add authentication, request limits, metrics/tracing, retention, recovery tests, and representative load tests. This in-memory implementation intentionally keeps only live state to match the clarified assignment scope.

## Verification

Run `.\mvnw.cmd clean test`. The suite covers validation and partial acceptance, duplicate and concurrent ingestion, same-batch ordering and late-packet handling, alert boundaries and transitions, offline lifecycle checks, zone overlap/boundaries, and OpenAPI endpoint documentation.

Before creating a submission ZIP, run `.\mvnw.cmd clean` and exclude the generated `target\` directory. `.gitignore` prevents it from being committed but does not automatically exclude it from a manually-created archive.

## Interview demo and screenshots

1. Start the server with `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=test"`.
2. Import the Postman collection and environment from `postman/`, then select **Telematics Local**.
3. Use **Set test clock**, **Ingest overspeed demo**, and **List overspeed alerts**; verify the request counts and the `SCHOOL-DPS` alert details.
4. Run **Validate partial batch and duplicate idempotency**; verify accepted `1`, duplicate `1`, rejected `1`.
5. Open `http://localhost:8080/swagger-ui/index.html` for the Swagger UI. The test clock endpoint is intentionally visible only when the test profile is enabled.
6. Capture actual Postman response-pane and Swagger UI screenshots in the interviewer’s requested format. No screenshots are committed by this project.
