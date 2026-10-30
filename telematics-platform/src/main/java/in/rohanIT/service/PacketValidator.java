package in.rohanIT.service;

import in.rohanIT.config.AlertProperties;
import in.rohanIT.dto.PacketRequest;
import in.rohanIT.model.Packet;
import java.time.Instant;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class PacketValidator {
    private final AlertProperties properties;

    public PacketValidator(AlertProperties properties) {
        this.properties = properties;
    }

    public Packet validate(JsonNode node, Instant serverTime) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("Packet must be a JSON object");
        }

        String deviceId = requiredText(node, "deviceId");
        String timestampText = requiredText(node, "timestamp");
        Instant timestamp;
        try {
            timestamp = Instant.parse(timestampText);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("timestamp must be a valid ISO-8601 instant in UTC");
        }

        double lat = requiredNumber(node, "lat");
        double lon = requiredNumber(node, "lon");
        double speed = requiredNumber(node, "speedKmh");
        JsonNode ignition = node.get("ignition");
        if (ignition == null || !ignition.isBoolean()) {
            throw new IllegalArgumentException("ignition is required and must be a boolean");
        }
        if (lat < -90 || lat > 90) {
            throw new IllegalArgumentException("lat must be between -90 and 90");
        }
        if (lon < -180 || lon > 180) {
            throw new IllegalArgumentException("lon must be between -180 and 180");
        }
        if (speed < 0) {
            throw new IllegalArgumentException("speedKmh must not be negative");
        }
        if (timestamp.isAfter(serverTime.plus(properties.packet().futureTolerance()))) {
            throw new IllegalArgumentException("timestamp must not be more than "
                    + properties.packet().futureTolerance() + " in the future");
        }

        PacketRequest request = new PacketRequest(deviceId, timestamp, lat, lon, speed, ignition.booleanValue());
        return new Packet(request.deviceId(), request.timestamp(), request.lat(), request.lon(),
                request.speedKmh(), request.ignition());
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString() || value.stringValue().isBlank()) {
            throw new IllegalArgumentException(field + " is required and must be a non-empty string");
        }
        return value.stringValue().trim();
    }

    private double requiredNumber(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(field + " is required and must be a number");
        }
        double number = value.doubleValue();
        if (!Double.isFinite(number)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
        return number;
    }
}
