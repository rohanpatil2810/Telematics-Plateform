package in.rohanIT.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "alerts")
public record AlertProperties(
        Overspeed overspeed,
        Fatigue fatigue,
        Offline offline,
        Packet packet
) {
    public record Overspeed(double tolerancePercent, Duration openDuration, Duration closeDuration) {}
    public record Fatigue(Duration drivingThreshold, Duration singleBreakReset,
                          Duration firstSplitBreak, Duration secondSplitBreak) {}
    public record Offline(Duration timeout, long checkIntervalMs) {}
    public record Packet(Duration futureTolerance) {}
}
