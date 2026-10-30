package in.rohanIT.service;

import in.rohanIT.model.Packet;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ZoneServiceTest {
    private final ZoneService zoneService = new ZoneService(new ObjectMapper());

    @Test
    void lowestOverlappingZoneLimitApplies() {
        var limit = zoneService.limitAt(packet(23.01, 72.555));

        assertEquals("SCHOOL-DPS", limit.zoneId());
        assertEquals(30.0, limit.speedLimitKmh());
    }

    @Test
    void defaultLimitAppliesOutsideEveryZone() {
        var limit = zoneService.limitAt(packet(23.2, 72.7));

        assertEquals("DEFAULT", limit.zoneId());
        assertEquals(80.0, limit.speedLimitKmh());
    }

    @Test
    void polygonBoundaryIsIncluded() {
        var highwayBoundary = zoneService.limitAt(packet(23.0, 72.55));
        var schoolBoundary = zoneService.limitAt(packet(23.005, 72.555));

        assertEquals("HIGHWAY-NH48", highwayBoundary.zoneId());
        assertEquals("SCHOOL-DPS", schoolBoundary.zoneId());
    }

    private Packet packet(double lat, double lon) {
        return new Packet("ZONE-TEST", java.time.Instant.EPOCH, lat, lon, 0, false);
    }
}
