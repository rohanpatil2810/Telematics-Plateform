package in.rohanIT.interfaces;

import in.rohanIT.model.DeviceState;
import java.util.Collection;

public interface DeviceStateRepository {
    DeviceState stateFor(String deviceId);

    Collection<DeviceState> allStates();
}
