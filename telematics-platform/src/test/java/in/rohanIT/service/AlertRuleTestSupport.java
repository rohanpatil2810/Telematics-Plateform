package in.rohanIT.service;

import in.rohanIT.config.AlertProperties;
import in.rohanIT.model.DeviceState;
import in.rohanIT.model.Packet;
import java.time.Duration;
import java.time.Instant;

final class AlertRuleTestSupport {
    static final Instant BASE = Instant.parse("2026-10-26T09:00:00Z");

    private AlertRuleTestSupport() {
    }

    static AlertProperties properties() {
        return new AlertProperties(
                new AlertProperties.Overspeed(5, Duration.ofSeconds(30), Duration.ofSeconds(15)),
                new AlertProperties.Fatigue(Duration.ofMinutes(270), Duration.ofMinutes(45),
                        Duration.ofMinutes(15), Duration.ofMinutes(30)),
                new AlertProperties.Offline(Duration.ofMinutes(15), 60_000),
                new AlertProperties.Packet(Duration.ofMinutes(1)));
    }

    static Packet packet(String device, Instant time, double lat, double lon,
                         double speed, boolean ignition) {
        return new Packet(device, time, lat, lon, speed, ignition);
    }

    static Packet packetAtSeconds(String device, long seconds, double speed, boolean ignition) {
        return packet(device, BASE.plusSeconds(seconds), 23.01, 72.555, speed, ignition);
    }

    static DeviceState state(String deviceId) {
        return new DeviceState(deviceId);
    }

    static void apply(in.rohanIT.interfaces.AlertRule rule, DeviceState state, Packet packet) {
        Packet previous = state.latestPacket();
        state.setLatestPacket(packet);
        rule.onPacket(state, packet, previous, packet.timestamp());
    }
}
