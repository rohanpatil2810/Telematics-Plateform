package in.rohanIT.service;

import in.rohanIT.config.AlertProperties;
import in.rohanIT.config.AdjustableClock;
import in.rohanIT.interfaces.AlertRule;
import in.rohanIT.model.AlertStatus;
import in.rohanIT.model.AlertType;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketIngestionServiceTest {
    @Test
    void acceptsValidPacketsRejectsInvalidAndIgnoresDuplicates() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AdjustableClock clock = new AdjustableClock(AlertRuleTestSupport.BASE, ZoneOffset.UTC);
        PacketIngestionService service = service(clock, mapper);
        var request = mapper.readTree("""
                [
                  {"deviceId":"V1","timestamp":"2026-10-26T09:00:00Z","lat":23,"lon":72,"speedKmh":10,"ignition":true},
                  {"deviceId":"V2","timestamp":"2026-10-26T09:00:00Z","lat":91,"lon":72,"speedKmh":10,"ignition":true},
                  {"deviceId":"V1","timestamp":"2026-10-26T09:00:00Z","lat":23,"lon":72,"speedKmh":10,"ignition":true},
                  {"deviceId":"V3","timestamp":"2026-10-26T09:02:00Z","lat":23,"lon":72,"speedKmh":10,"ignition":true}
                ]
                """);

        var response = service.ingest(request);

        assertEquals(1, response.acceptedCount());
        assertEquals(1, response.duplicateCount());
        assertEquals(0, response.outOfOrderIgnoredCount());
        assertEquals(2, response.rejectedCount());
        assertTrue(response.rejections().get(0).reason().contains("lat"));
        assertTrue(service.alerts("V1", AlertType.DEVICE_OFFLINE).isEmpty());
    }

    @Test
    void rejectsMissingFieldsInvalidNumbersAndFutureTimestampsIndividually() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AdjustableClock clock = new AdjustableClock(AlertRuleTestSupport.BASE, ZoneOffset.UTC);
        PacketIngestionService service = service(clock, mapper);
        var request = mapper.readTree("""
                [
                  {"timestamp":"2026-10-26T09:00:00Z","lat":23,"lon":72,"speedKmh":10,"ignition":true},
                  {"deviceId":"BAD-LON","timestamp":"2026-10-26T09:00:00Z","lat":23,"lon":181,"speedKmh":10,"ignition":true},
                  {"deviceId":"BAD-SPEED","timestamp":"2026-10-26T09:00:00Z","lat":23,"lon":72,"speedKmh":-1,"ignition":true},
                  {"deviceId":"FUTURE","timestamp":"2026-10-26T09:02:00Z","lat":23,"lon":72,"speedKmh":10,"ignition":true},
                  {"deviceId":"BAD-TIME","timestamp":"not-an-instant","lat":23,"lon":72,"speedKmh":10,"ignition":true},
                  {"deviceId":"NO-IGNITION","timestamp":"2026-10-26T09:00:00Z","lat":23,"lon":72,"speedKmh":10}
                ]
                """);

        var response = service.ingest(request);

        assertEquals(0, response.acceptedCount());
        assertEquals(6, response.rejectedCount());
        assertTrue(response.rejections().get(0).reason().contains("deviceId"));
        assertTrue(response.rejections().get(1).reason().contains("lon"));
        assertTrue(response.rejections().get(2).reason().contains("speedKmh"));
        assertTrue(response.rejections().get(3).reason().contains("future"));
        assertTrue(response.rejections().get(4).reason().contains("timestamp"));
        assertTrue(response.rejections().get(5).reason().contains("ignition"));
    }

    @Test
    void sortsPacketsWithinRequestAndIgnoresLatePacketsAcrossRequests() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AdjustableClock clock = new AdjustableClock(AlertRuleTestSupport.BASE.plusSeconds(60), ZoneOffset.UTC);
        PacketIngestionService service = service(clock, mapper);
        var firstBatch = mapper.readTree("""
                [
                  {"deviceId":"ORDERED","timestamp":"2026-10-26T09:00:30Z","lat":23.01,"lon":72.555,"speedKmh":50,"ignition":true},
                  {"deviceId":"ORDERED","timestamp":"2026-10-26T09:00:00Z","lat":23.01,"lon":72.555,"speedKmh":50,"ignition":true},
                  {"deviceId":"ORDERED","timestamp":"2026-10-26T09:00:15Z","lat":23.01,"lon":72.555,"speedKmh":50,"ignition":true}
                ]
                """);

        var batchResponse = service.ingest(firstBatch);
        var openedAlert = service.alerts("ORDERED", AlertType.OVERSPEED).get(0);
        var latePacket = mapper.readTree("""
                [{"deviceId":"ORDERED","timestamp":"2026-10-26T09:00:20Z",
                  "lat":23.01,"lon":72.555,"speedKmh":50,"ignition":true}]
                """);
        var lateResponse = service.ingest(latePacket);

        assertEquals(3, batchResponse.acceptedCount());
        assertEquals(0, batchResponse.outOfOrderIgnoredCount());
        assertEquals(1, lateResponse.acceptedCount());
        assertEquals(1, lateResponse.outOfOrderIgnoredCount());
        assertEquals(openedAlert.alertId(), service.alerts("ORDERED", AlertType.OVERSPEED).get(0).alertId());
    }

    @Test
    void reconcilesOfflineAlertAfterEntireBufferedBatch() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AdjustableClock clock = new AdjustableClock(
                Instant.parse("2026-10-27T10:10:30Z"), ZoneOffset.UTC);
        PacketIngestionService service = service(clock, mapper);
        var initialPackets = mapper.readTree("""
                [{"deviceId":"V302","timestamp":"2026-10-27T10:10:00Z",
                  "lat":23.03,"lon":72.55,"speedKmh":40,"ignition":true}]
                """);
        service.ingest(initialPackets);

        clock.setInstant(Instant.parse("2026-10-27T10:25:00Z"));
        service.checkOfflineAlerts();
        assertEquals(AlertStatus.OPEN,
                service.alerts("V302", AlertType.DEVICE_OFFLINE).get(0).status());

        clock.setInstant(Instant.parse("2026-10-27T10:32:30Z"));
        StringBuilder bufferedPackets = new StringBuilder("[");
        for (int minute = 11; minute <= 32; minute++) {
            if (minute > 11) {
                bufferedPackets.append(',');
            }
            boolean ignition = minute < 32;
            bufferedPackets.append("""
                    {"deviceId":"V302","timestamp":"2026-10-27T10:%02d:00Z",
                     "lat":23.03,"lon":72.55,"speedKmh":%d,"ignition":%b}
                    """.formatted(minute, ignition ? 40 : 0, ignition));
        }
        bufferedPackets.append(']');

        var response = service.ingest(mapper.readTree(bufferedPackets.toString()));

        assertEquals(22, response.acceptedCount());
        var alert = service.alerts("V302", AlertType.DEVICE_OFFLINE).get(0);
        assertEquals(AlertStatus.CANCELLED, alert.status());
        assertEquals(Instant.parse("2026-10-27T10:32:00Z"), alert.endTime());
    }

    @Test
    void closesOfflineAlertAtFirstQualifyingGapInBufferedBatch() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AdjustableClock clock = new AdjustableClock(
                Instant.parse("2026-10-27T10:10:30Z"), ZoneOffset.UTC);
        PacketIngestionService service = service(clock, mapper);
        service.ingest(mapper.readTree("""
                [{"deviceId":"V-GAP","timestamp":"2026-10-27T10:10:00Z",
                  "lat":23.03,"lon":72.55,"speedKmh":40,"ignition":true}]
                """));

        clock.setInstant(Instant.parse("2026-10-27T10:25:00Z"));
        service.checkOfflineAlerts();
        clock.setInstant(Instant.parse("2026-10-27T10:32:30Z"));
        var bufferedPackets = mapper.readTree("""
                [
                  {"deviceId":"V-GAP","timestamp":"2026-10-27T10:11:00Z",
                   "lat":23.03,"lon":72.55,"speedKmh":40,"ignition":true},
                  {"deviceId":"V-GAP","timestamp":"2026-10-27T10:26:00Z",
                   "lat":23.03,"lon":72.55,"speedKmh":40,"ignition":true},
                  {"deviceId":"V-GAP","timestamp":"2026-10-27T10:32:00Z",
                   "lat":23.03,"lon":72.55,"speedKmh":0,"ignition":false}
                ]
                """);

        service.ingest(bufferedPackets);

        var alert = service.alerts("V-GAP", AlertType.DEVICE_OFFLINE).get(0);
        assertEquals(AlertStatus.CLOSED, alert.status());
        assertEquals(Instant.parse("2026-10-27T10:11:00Z"), alert.startTime());
        assertEquals(Instant.parse("2026-10-27T10:26:00Z"), alert.endTime());
    }

    @Test
    void acceptsConcurrentDuplicateTimestampOnlyOnce() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AdjustableClock clock = new AdjustableClock(AlertRuleTestSupport.BASE, ZoneOffset.UTC);
        PacketIngestionService service = service(clock, mapper);
        var request = mapper.readTree("""
                [{"deviceId":"CONCURRENT","timestamp":"2026-10-26T09:00:00Z",
                  "lat":23,"lon":72,"speedKmh":10,"ignition":true}]
                """);
        int requests = 16;
        var pool = Executors.newFixedThreadPool(requests);
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(requests);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger duplicates = new AtomicInteger();
        for (int i = 0; i < requests; i++) {
            pool.execute(() -> {
                try {
                    start.await();
                    var result = service.ingest(request);
                    accepted.addAndGet(result.acceptedCount());
                    duplicates.addAndGet(result.duplicateCount());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(1, accepted.get());
        assertEquals(requests - 1, duplicates.get());
    }

    @Test
    void safelySerializesConcurrentSameDevicePacketsAndOfflineChecks() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AdjustableClock clock = new AdjustableClock(AlertRuleTestSupport.BASE.plusSeconds(60), ZoneOffset.UTC);
        PacketIngestionService service = service(clock, mapper);
        int packetCount = 16;
        var pool = Executors.newFixedThreadPool(packetCount + 1);
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(packetCount + 1);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger outOfOrder = new AtomicInteger();
        pool.execute(() -> {
            try {
                start.await();
                for (int i = 0; i < 20; i++) {
                    service.checkOfflineAlerts();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });
        for (int i = 0; i < packetCount; i++) {
            int packetIndex = i;
            pool.execute(() -> {
                try {
                    start.await();
                    var packet = mapper.readTree("""
                            [{"deviceId":"RACE","timestamp":"%s","lat":23.01,"lon":72.555,"speedKmh":50,"ignition":true}]
                            """.formatted(AlertRuleTestSupport.BASE.plusSeconds(packetIndex * 3L)));
                    var response = service.ingest(packet);
                    accepted.addAndGet(response.acceptedCount());
                    outOfOrder.addAndGet(response.outOfOrderIgnoredCount());
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(packetCount, accepted.get());
        assertTrue(outOfOrder.get() >= 0 && outOfOrder.get() < packetCount);
    }

    private PacketIngestionService service(AdjustableClock clock, ObjectMapper mapper) {
        AlertProperties properties = AlertRuleTestSupport.properties();
        ZoneService zones = new ZoneService(mapper);
        List<AlertRule> rules = List.of(
                new OverspeedAlertService(zones, properties),
                new FatigueAlertService(properties),
                new DeviceOfflineAlertService(properties));
        return new PacketIngestionService(new DeviceStateStore(), rules, clock,
                new PacketValidator(properties));
    }
}
