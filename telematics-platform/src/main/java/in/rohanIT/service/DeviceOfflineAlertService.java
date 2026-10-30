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
import org.springframework.stereotype.Service;

@Service
public class DeviceOfflineAlertService implements AlertRule {
    private final Duration timeout;

    public DeviceOfflineAlertService(AlertProperties properties) {
        timeout = properties.offline().timeout();
    }

    @Override
    public AlertType type() {
        return AlertType.DEVICE_OFFLINE;
    }

    @Override
    public void onPacket(DeviceState state, Packet packet, Packet previousPacket, Instant serverTime) {
        Alert currentAlert = state.alert(type());
        if (currentAlert == null || currentAlert.status() != AlertStatus.OPEN) {
            return;
        }

        if (previousPacket != null && previousPacket.ignition()
                && elapsed(previousPacket.timestamp(), packet.timestamp()).compareTo(timeout) >= 0) {
            state.updateAlert(currentAlert.withStatus(
                    AlertStatus.CLOSED, previousPacket.timestamp(), packet.timestamp()));
        }
    }

    @Override
    public void check(DeviceState state, Instant serverTime) {
        Packet latestPacket = state.latestPacket();
        if (latestPacket == null) {
            return;
        }

        Alert currentAlert = state.alert(type());
        if (currentAlert != null && currentAlert.status() == AlertStatus.OPEN) {
            if (!latestPacket.ignition()) {
                state.updateAlert(currentAlert.withStatus(
                        AlertStatus.CANCELLED, currentAlert.startTime(), latestPacket.timestamp()));
                return;
            }

            if (isSilent(latestPacket.timestamp(), serverTime)) {
                state.updateAlert(currentAlert.withStatus(
                        AlertStatus.OPEN, latestPacket.timestamp(), null));
            } else {
                state.updateAlert(currentAlert.withStatus(
                        AlertStatus.CANCELLED, currentAlert.startTime(), latestPacket.timestamp()));
            }
        } else if (latestPacket.ignition() && isSilent(latestPacket.timestamp(), serverTime)) {
            state.updateAlert(new Alert(UUID.randomUUID().toString(), state.deviceId(), type(),
                    AlertStatus.OPEN, latestPacket.timestamp(), null, Map.of()));
        }
    }

    private boolean isSilent(Instant lastPacketTime, Instant serverTime) {
        return elapsed(lastPacketTime, serverTime).compareTo(timeout) >= 0;
    }

    private Duration elapsed(Instant from, Instant to) {
        return Duration.between(from, to);
    }
}
