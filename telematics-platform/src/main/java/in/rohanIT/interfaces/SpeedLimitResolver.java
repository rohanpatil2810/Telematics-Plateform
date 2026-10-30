package in.rohanIT.interfaces;

import in.rohanIT.model.Packet;
import in.rohanIT.model.SpeedLimit;

public interface SpeedLimitResolver {
    SpeedLimit limitAt(Packet packet);
}
