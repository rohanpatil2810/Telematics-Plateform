package in.rohanIT.service;

import in.rohanIT.config.AlertProperties;
import in.rohanIT.interfaces.AlertRule;
import in.rohanIT.model.Alert;
import in.rohanIT.model.AlertStatus;
import in.rohanIT.model.AlertType;
import in.rohanIT.model.DeviceState;
import in.rohanIT.model.Packet;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class FatigueAlertService implements AlertRule {
    private final Duration drivingThreshold;
    private final Duration singleBreakReset;
    private final Duration firstSplitBreak;
    private final Duration secondSplitBreak;
    private final ConcurrentHashMap<String, Progress> progressByDevice = new ConcurrentHashMap<>();

    public FatigueAlertService(AlertProperties properties) {
        drivingThreshold = properties.fatigue().drivingThreshold();
        singleBreakReset = properties.fatigue().singleBreakReset();
        firstSplitBreak = properties.fatigue().firstSplitBreak();
        secondSplitBreak = properties.fatigue().secondSplitBreak();
    }

    @Override
    public AlertType type() {
        return AlertType.FATIGUE;
    }

    @Override
    public void onPacket(DeviceState state, Packet packet, Packet previousPacket, Instant serverTime) {
        Progress progress = progressByDevice.computeIfAbsent(state.deviceId(), ignored -> new Progress());
        boolean currentDriving = isDriving(packet);
        if (previousPacket == null) {
            progress.previousDriving = currentDriving;
            return;
        }

        Duration interval = Duration.between(previousPacket.timestamp(), packet.timestamp());
        if (interval.isNegative() || interval.isZero()) {
            return;
        }

        if (progress.previousDriving) {
            Duration updatedDrivingTime = progress.drivingTime.plus(interval);
            if (!progress.fatigueOpen && updatedDrivingTime.compareTo(drivingThreshold) >= 0) {
                Instant thresholdAt = previousPacket.timestamp()
                        .plus(drivingThreshold.minus(progress.drivingTime));
                state.updateAlert(new Alert(UUID.randomUUID().toString(), state.deviceId(), type(),
                        AlertStatus.OPEN, thresholdAt, null, Map.of()));
                progress.fatigueOpen = true;
            }
            progress.drivingTime = updatedDrivingTime;
            if (!currentDriving) {
                progress.breakStart = packet.timestamp();
                progress.breakDuration = Duration.ZERO;
                progress.breakReset = false;
            }
        } else {
            if (progress.breakStart == null) {
                progress.breakStart = previousPacket.timestamp();
            }
            progress.breakDuration = progress.breakDuration.plus(interval);
            applyBreakReset(state, progress);

            if (currentDriving) {
                if (!progress.breakReset
                        && progress.breakDuration.compareTo(firstSplitBreak) >= 0
                        && progress.breakDuration.compareTo(singleBreakReset) < 0) {
                    progress.hasFirstSplitBreak = true;
                }
                progress.breakStart = null;
                progress.breakDuration = Duration.ZERO;
                progress.breakReset = false;
            }
        }
        progress.previousDriving = currentDriving;
    }

    private void applyBreakReset(DeviceState state, Progress progress) {
        Duration resetThreshold = progress.hasFirstSplitBreak ? secondSplitBreak : singleBreakReset;
        if (progress.breakReset || progress.breakDuration.compareTo(resetThreshold) < 0) {
            return;
        }
        Instant resetAt = progress.breakStart.plus(resetThreshold);
        Alert currentAlert = state.alert(type());
        if (progress.fatigueOpen && currentAlert != null && currentAlert.status() == AlertStatus.OPEN) {
            state.updateAlert(currentAlert.withStatus(AlertStatus.CLOSED,
                    currentAlert.startTime(), resetAt));
        }
        progress.drivingTime = Duration.ZERO;
        progress.fatigueOpen = false;
        progress.hasFirstSplitBreak = false;
        progress.breakReset = true;
    }

    private boolean isDriving(Packet packet) {
        return packet.ignition() && packet.speedKmh() > 5.0;
    }

    private static final class Progress {
        private Duration drivingTime = Duration.ZERO;
        private Duration breakDuration = Duration.ZERO;
        private Instant breakStart;
        private boolean previousDriving;
        private boolean hasFirstSplitBreak;
        private boolean breakReset;
        private boolean fatigueOpen;
    }
}
