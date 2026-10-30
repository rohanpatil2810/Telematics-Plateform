package in.rohanIT.dto;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

public record PacketRequest(
        @Schema(description = "Unique vehicle/device identifier", example = "V101")
        String deviceId,
        @Schema(description = "Device packet timestamp in UTC", example = "2026-10-26T09:00:00Z")
        Instant timestamp,
        @Schema(description = "Latitude in degrees, from -90 to 90", example = "23.01")
        double lat,
        @Schema(description = "Longitude in degrees, from -180 to 180", example = "72.555")
        double lon,
        @Schema(description = "Vehicle speed in km/h; must be non-negative", example = "50")
        double speedKmh,
        @Schema(description = "Whether the vehicle ignition is on", example = "true")
        boolean ignition
) {
}
