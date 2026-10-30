package in.rohanIT.service;

import in.rohanIT.dto.PacketRejection;
import in.rohanIT.dto.IngestionResponse;
import in.rohanIT.interfaces.AlertRule;
import in.rohanIT.interfaces.DeviceStateRepository;
import in.rohanIT.model.Alert;
import in.rohanIT.model.AlertStatus;
import in.rohanIT.model.AlertType;
import in.rohanIT.model.DeviceState;
import in.rohanIT.model.Packet;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

@Service
public class PacketIngestionService {
    private final DeviceStateRepository stateRepository;
    private final List<AlertRule> alertRules;
    private final Clock clock;
    private final PacketValidator packetValidator;

    public PacketIngestionService(DeviceStateRepository stateRepository, List<AlertRule> alertRules,
                                  Clock clock, PacketValidator packetValidator) {
        this.stateRepository = stateRepository;
        this.alertRules = List.copyOf(alertRules);
        this.clock = clock;
        this.packetValidator = packetValidator;
    }

    public IngestionResponse ingest(JsonNode packetNodes) {
        int acceptedCount = 0;
        int duplicateCount = 0;
        int outOfOrderIgnoredCount = 0;
        List<PacketRejection> rejections = new ArrayList<>();
        Map<String, List<IndexedPacket>> packetsByDevice = new LinkedHashMap<>();
        Instant requestTime = clock.instant();

        for (int index = 0; index < packetNodes.size(); index++) {
            JsonNode packetNode = packetNodes.get(index);
            try {
                Packet packet = packetValidator.validate(packetNode, requestTime);
                packetsByDevice.computeIfAbsent(packet.deviceId(), ignored -> new ArrayList<>())
                        .add(new IndexedPacket(packet));
            } catch (IllegalArgumentException exception) {
                rejections.add(new PacketRejection(index, text(packetNode, "deviceId"),
                        text(packetNode, "timestamp"), exception.getMessage()));
            }
        }

        for (Map.Entry<String, List<IndexedPacket>> deviceBatch : packetsByDevice.entrySet()) {
            DeviceState state = stateRepository.stateFor(deviceBatch.getKey());
            List<IndexedPacket> chronologicalBatch = deviceBatch.getValue().stream()
                    .sorted(Comparator.comparing(indexed -> indexed.packet().timestamp()))
                    .toList();

            synchronized (state) {
                for (IndexedPacket indexedPacket : chronologicalBatch) {
                    Packet packet = indexedPacket.packet();
                    if (!state.rememberPacketTime(packet.timestamp())) {
                        duplicateCount++;
                        continue;
                    }
                    acceptedCount++;

                    Packet previousPacket = state.latestPacket();
                    if (previousPacket != null && packet.timestamp().isBefore(previousPacket.timestamp())) {
                        outOfOrderIgnoredCount++;
                        continue;
                    }

                    state.setLatestPacket(packet);
                    Instant processedAt = clock.instant();
                    for (AlertRule alertRule : alertRules) {
                        alertRule.onPacket(state, packet, previousPacket, processedAt);
                    }
                }

                Alert offlineAlert = state.alert(AlertType.DEVICE_OFFLINE);
                if (offlineAlert != null && offlineAlert.status() == AlertStatus.OPEN) {
                    alertRules.stream()
                            .filter(rule -> rule.type() == AlertType.DEVICE_OFFLINE)
                            .findFirst()
                            .ifPresent(rule -> rule.check(state, clock.instant()));
                }
            }
        }

        return new IngestionResponse(acceptedCount, duplicateCount, outOfOrderIgnoredCount,
                rejections.size(), List.copyOf(rejections));
    }

    public List<Alert> alerts(String deviceId, AlertType type) {
        return stateRepository.allStates().stream()
                .filter(state -> deviceId == null || deviceId.isBlank()
                        || state.deviceId().equals(deviceId))
                .flatMap(state -> {
                    synchronized (state) {
                        return state.alerts().stream();
                    }
                })
                .filter(alert -> type == null || alert.type() == type)
                .sorted(Comparator.comparing(Alert::startTime)
                        .thenComparing(Alert::deviceId)
                        .thenComparing(Alert::type))
                .toList();
    }

    public void checkOfflineAlerts() {
        Instant now = clock.instant();
        for (DeviceState state : stateRepository.allStates()) {
            synchronized (state) {
                for (AlertRule alertRule : alertRules) {
                    if (alertRule.type() == AlertType.DEVICE_OFFLINE) {
                        alertRule.check(state, now);
                    }
                }
            }
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && (value.isString() || value.isNumber() || value.isBoolean())
                ? value.asString()
                : null;
    }

    private record IndexedPacket(Packet packet) {
    }
}
