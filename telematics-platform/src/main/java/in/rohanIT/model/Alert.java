package in.rohanIT.model;

import java.time.Instant;
import java.util.Map;

public record Alert(
        String alertId,
        String deviceId,
        AlertType type,
        AlertStatus status,
        Instant startTime,
        Instant endTime,
        Map<String, Object> details
) {
    public Alert {
        details = Map.copyOf(details);
    }

    public Alert withStatus(AlertStatus newStatus, Instant newStartTime, Instant newEndTime) {
        return new Alert(alertId, deviceId, type, newStatus, newStartTime, newEndTime, details);
    }
}
