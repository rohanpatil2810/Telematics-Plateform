package in.rohanIT.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DeviceState {
    private final String deviceId;
    private final Set<Instant> seenPacketTimes = new HashSet<>();
    private final Map<AlertType, Alert> currentAlerts = new EnumMap<>(AlertType.class);
    private Packet latestPacket;

    public DeviceState(String deviceId) {
        this.deviceId = deviceId;
    }

    public String deviceId() {
        return deviceId;
    }

    public boolean rememberPacketTime(Instant timestamp) {
        return seenPacketTimes.add(timestamp);
    }

    public Packet latestPacket() {
        return latestPacket;
    }

    public void setLatestPacket(Packet packet) {
        latestPacket = packet;
    }

    public Alert alert(AlertType type) {
        return currentAlerts.get(type);
    }

    public void updateAlert(Alert alert) {
        currentAlerts.put(alert.type(), alert);
    }

    public List<Alert> alerts() {
        return new ArrayList<>(currentAlerts.values());
    }
}
