package in.rohanIT.dto;

import in.rohanIT.model.Alert;
import in.rohanIT.model.AlertStatus;
import in.rohanIT.model.AlertType;
import java.time.Instant;
import java.util.Map;

public record AlertResponse(
        String alertId,
        String deviceId,
        AlertType type,
        AlertStatus status,
        Instant startTime,
        Instant endTime,
        Map<String, Object> details
) {
    public static AlertResponse from(Alert alert) {
        return new AlertResponse(alert.alertId(), alert.deviceId(), alert.type(), alert.status(),
                alert.startTime(), alert.endTime(), alert.details());
    }
}
