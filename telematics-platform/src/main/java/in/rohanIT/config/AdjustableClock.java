package in.rohanIT.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

public final class AdjustableClock extends Clock {
    private final AtomicReference<Instant> currentInstant;
    private final ZoneId zone;

    public AdjustableClock(Instant initialInstant, ZoneId zone) {
        this.currentInstant = new AtomicReference<>(initialInstant);
        this.zone = zone;
    }

    public void setInstant(Instant instant) {
        currentInstant.set(instant);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zoneId) {
        return new AdjustableClock(instant(), zoneId);
    }

    @Override
    public Instant instant() {
        return currentInstant.get();
    }
}
