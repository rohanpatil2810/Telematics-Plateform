package in.rohanIT.service;

import in.rohanIT.config.AlertProperties;
import in.rohanIT.interfaces.AlertRule;
import in.rohanIT.interfaces.SpeedLimitResolver;
import in.rohanIT.model.Alert;
import in.rohanIT.model.AlertStatus;
import in.rohanIT.model.AlertType;
import in.rohanIT.model.DeviceState;
import in.rohanIT.model.Packet;
import in.rohanIT.model.SpeedLimit;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class OverspeedAlertService implements AlertRule {
    private final SpeedLimitResolver speedLimitResolver;
    private final Duration openDuration;
    private final Duration closeDuration;
    private final double tolerance;
    private final ConcurrentHashMap<String, Progress> progressByDevice = new ConcurrentHashMap<>();

    public OverspeedAlertService(SpeedLimitResolver speedLimitResolver, AlertProperties properties) {
        this.speedLimitResolver = speedLimitResolver;
        openDuration = properties.overspeed().openDuration();
        closeDuration = properties.overspeed().closeDuration();
        tolerance = 1.0 + properties.overspeed().tolerancePercent() / 100.0;
    }

    @Override
    public AlertType type() {
        return AlertType.OVERSPEED;
    }

    @Override
    public void onPacket(DeviceState state, Packet packet, Packet previousPacket, Instant serverTime) {
        Progress progress = progressByDevice.computeIfAbsent(state.deviceId(), ignored -> new Progress());
        Sample sample = sample(packet);
        Alert openAlert = state.alert(type());

        if (openAlert != null && openAlert.status() == AlertStatus.OPEN) {
            if (sample.classification() == Classification.COMPLIANT) {
                if (progress.compliantStart == null) {
                    progress.compliantStart = packet.timestamp();
                }
                progress.include(sample);
                if (elapsed(progress.compliantStart, packet.timestamp()).compareTo(closeDuration) >= 0) {
                    state.updateAlert(progress.toAlert(state.deviceId(), openAlert.alertId(),
                            openAlert.startTime(), AlertStatus.CLOSED, progress.compliantStart));
                    progress.reset();
                } else {
                    state.updateAlert(progress.toAlert(state.deviceId(), openAlert.alertId(),
                            openAlert.startTime(), AlertStatus.OPEN, null));
                }
            } else {
                progress.compliantStart = null;
                progress.include(sample);
                state.updateAlert(progress.toAlert(state.deviceId(), openAlert.alertId(),
                        openAlert.startTime(), AlertStatus.OPEN, null));
            }
            return;
        }

        if (sample.classification() != Classification.VIOLATING) {
            progress.reset();
            return;
        }

        if (progress.violationStart == null) {
            progress.violationStart = packet.timestamp();
        }
        progress.include(sample);
        if (elapsed(progress.violationStart, packet.timestamp()).compareTo(openDuration) >= 0) {
            state.updateAlert(progress.toAlert(state.deviceId(), UUID.randomUUID().toString(),
                    progress.violationStart, AlertStatus.OPEN, null));
        }
    }

    private Sample sample(Packet packet) {
        SpeedLimit limit = speedLimitResolver.limitAt(packet);
        Classification classification;
        if (packet.speedKmh() > limit.speedLimitKmh() * tolerance) {
            classification = Classification.VIOLATING;
        } else if (packet.speedKmh() <= limit.speedLimitKmh()) {
            classification = Classification.COMPLIANT;
        } else {
            classification = Classification.NEITHER;
        }
        return new Sample(packet, limit.zoneId(), limit.speedLimitKmh(), classification);
    }

    private Duration elapsed(Instant start, Instant end) {
        return Duration.between(start, end);
    }

    private enum Classification {
        VIOLATING,
        COMPLIANT,
        NEITHER
    }

    private record Sample(Packet packet, String zoneId, double limit, Classification classification) {
    }

    private static final class Progress {
        private Instant violationStart;
        private Instant compliantStart;
        private double maxSpeed;
        private double worstExcess = Double.NEGATIVE_INFINITY;
        private String worstZoneId;
        private double limitAtWorst;

        private void include(Sample sample) {
            maxSpeed = Math.max(maxSpeed, sample.packet().speedKmh());
            double excess = sample.packet().speedKmh() - sample.limit();
            if (excess > worstExcess) {
                worstExcess = excess;
                worstZoneId = sample.zoneId();
                limitAtWorst = sample.limit();
            }
        }

        private Alert toAlert(String deviceId, String alertId, Instant startTime,
                              AlertStatus status, Instant endTime) {
            Map<String, Object> details = new HashMap<>();
            details.put("maxSpeedKmh", maxSpeed);
            details.put("worstExcessKmh", worstExcess);
            details.put("worstZoneId", worstZoneId);
            details.put("limitAtWorstKmh", limitAtWorst);
            return new Alert(alertId, deviceId, AlertType.OVERSPEED, status, startTime, endTime, details);
        }

        private void reset() {
            violationStart = null;
            compliantStart = null;
            maxSpeed = 0;
            worstExcess = Double.NEGATIVE_INFINITY;
            worstZoneId = null;
            limitAtWorst = 0;
        }
    }
}
