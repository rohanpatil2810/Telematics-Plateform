package in.rohanIT.service;

import in.rohanIT.model.DeviceState;
import in.rohanIT.interfaces.DeviceStateRepository;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class DeviceStateStore implements DeviceStateRepository {
    private final ConcurrentHashMap<String, DeviceState> devices = new ConcurrentHashMap<>();

    @Override
    public DeviceState stateFor(String deviceId) {
        return devices.computeIfAbsent(deviceId, DeviceState::new);
    }

    @Override
    public Collection<DeviceState> allStates() {
        return devices.values();
    }
}
