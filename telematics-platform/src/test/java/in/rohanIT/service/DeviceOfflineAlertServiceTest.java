package in.rohanIT.service;

import in.rohanIT.model.AlertStatus;
import in.rohanIT.model.DeviceState;
import in.rohanIT.model.Packet;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeviceOfflineAlertServiceTest {
    private DeviceOfflineAlertService service;

    @BeforeEach
    void setUp() {
        service = new DeviceOfflineAlertService(AlertRuleTestSupport.properties());
    }

    @Test
    void opensAtFifteenMinutesAndIgnoresParkedDevice() {
        DeviceState justUnderThreshold = stateWith(packet(true, 0));
        service.check(justUnderThreshold, time(15).minusSeconds(1));
        assertNull(justUnderThreshold.alert(service.type()));

        DeviceState moving = stateWith(packet(true, 0));
        service.check(moving, time(15));
        assertEquals(AlertStatus.OPEN, moving.alert(service.type()).status());
        assertEquals(AlertRuleTestSupport.BASE, moving.alert(service.type()).startTime());

        DeviceState parked = stateWith(packet(false, 0));
        service.check(parked, time(60));
        assertNull(parked.alert(service.type()));
    }

    @Test
    void repeatedChecksKeepSingleOpenAlertAndNewPacketClosesForRealGap() {
        DeviceState state = stateWith(packet(true, 0));
        service.check(state, time(15));
        String alertId = state.alert(service.type()).alertId();
        service.check(state, time(16));
        assertEquals(alertId, state.alert(service.type()).alertId());
        assertEquals(1, state.alerts().size());

        Packet previous = state.latestPacket();
        Packet resumed = packet(true, 15);
        state.setLatestPacket(resumed);
        service.onPacket(state, resumed, previous, time(16));

        assertEquals(AlertStatus.CLOSED, state.alert(service.type()).status());
        assertEquals(AlertRuleTestSupport.BASE, state.alert(service.type()).startTime());
        assertEquals(time(15), state.alert(service.type()).endTime());
    }

    @Test
    void shortGapIsReconciledAfterTheBatchAndCancelsInsteadOfClosing() {
        DeviceState state = stateWith(packet(true, 0));
        service.check(state, time(15));
        Packet previous = state.latestPacket();
        Packet resumed = packet(true, 14);
        state.setLatestPacket(resumed);
        service.onPacket(state, resumed, previous, time(15));

        assertEquals(AlertStatus.OPEN, state.alert(service.type()).status());

        service.check(state, time(15));

        assertEquals(AlertStatus.CANCELLED, state.alert(service.type()).status());
        assertEquals(time(14), state.alert(service.type()).endTime());
    }

    @Test
    void canOpenANewOfflineEpisodeAfterThePreviousOneCloses() {
        DeviceState state = stateWith(packet(true, 0));
        service.check(state, time(15));
        Packet previous = state.latestPacket();
        Packet resumed = packet(true, 15);
        state.setLatestPacket(resumed);
        service.onPacket(state, resumed, previous, time(15));
        service.check(state, time(30));

        assertEquals(AlertStatus.OPEN, state.alert(service.type()).status());
        assertEquals(time(15), state.alert(service.type()).startTime());
        assertEquals(1, state.alerts().size());
    }

    @Test
    void offlineCheckUsesNewestPacketTimestampAndDoesNotAlertWhenNewestPacketIsParked() {
        DeviceState state = stateWith(packet(true, 0));
        Packet recent = packet(true, 2);
        state.setLatestPacket(recent);
        service.check(state, time(17));
        assertEquals(AlertStatus.OPEN, state.alert(service.type()).status());
        assertEquals(time(2), state.alert(service.type()).startTime());

        DeviceState parked = stateWith(packet(false, 0));
        service.check(parked, time(60));
        assertNull(parked.alert(service.type()));

        DeviceState resumedAndParked = stateWith(packet(true, 0));
        service.check(resumedAndParked, time(15));
        Packet parkedPacket = packet(false, 16);
        resumedAndParked.setLatestPacket(parkedPacket);
        service.onPacket(resumedAndParked, parkedPacket, packet(true, 15), time(16));
        assertEquals(AlertStatus.OPEN, resumedAndParked.alert(service.type()).status());

        service.check(resumedAndParked, time(16));
        assertEquals(AlertStatus.CANCELLED, resumedAndParked.alert(service.type()).status());
        assertEquals(time(16), resumedAndParked.alert(service.type()).endTime());
    }

    private DeviceState stateWith(Packet packet) {
        DeviceState state = AlertRuleTestSupport.state(packet.deviceId());
        state.setLatestPacket(packet);
        return state;
    }

    private Packet packet(boolean ignition, long minutes) {
        return AlertRuleTestSupport.packet("V-OFFLINE",
                time(minutes), 23.0, 72.5, ignition ? 40 : 0, ignition);
    }

    private java.time.Instant time(long minutes) {
        return AlertRuleTestSupport.BASE.plus(Duration.ofMinutes(minutes));
    }
}
