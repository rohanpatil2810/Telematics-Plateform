package in.rohanIT.interfaces;

import in.rohanIT.model.AlertType;
import in.rohanIT.model.DeviceState;
import in.rohanIT.model.Packet;
import java.time.Instant;

public interface AlertRule {
    AlertType type();

    void onPacket(DeviceState state, Packet packet, Packet previousPacket, Instant serverTime);

    default void check(DeviceState state, Instant serverTime) {
    }
}
