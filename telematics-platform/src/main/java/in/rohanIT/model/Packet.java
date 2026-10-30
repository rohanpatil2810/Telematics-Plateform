package in.rohanIT.model;

import java.time.Instant;

public record Packet(
        String deviceId,
        Instant timestamp,
        double lat,
        double lon,
        double speedKmh,
        boolean ignition
) {
}
