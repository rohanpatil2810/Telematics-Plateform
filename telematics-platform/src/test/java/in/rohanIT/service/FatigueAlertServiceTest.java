package in.rohanIT.service;

import in.rohanIT.model.AlertStatus;
import in.rohanIT.model.DeviceState;
import in.rohanIT.model.Packet;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FatigueAlertServiceTest {
    private FatigueAlertService service;

    @BeforeEach
    void setUp() {
        service = new FatigueAlertService(AlertRuleTestSupport.properties());
    }

    @Test
    void opensAtFourHoursThirtyMinutesButNotAtFourHoursTwentyNineMinutes() {
        DeviceState underThreshold = AlertRuleTestSupport.state("FATIGUE-UNDER");
        apply(underThreshold, packetAt(0, 60, true));
        apply(underThreshold, packetAt(269, 60, true));
        assertNull(underThreshold.alert(service.type()));

        DeviceState exactThreshold = AlertRuleTestSupport.state("FATIGUE-EXACT");
        apply(exactThreshold, packetAt(0, 60, true));
        apply(exactThreshold, packetAt(270, 60, true));
        assertEquals(AlertStatus.OPEN, exactThreshold.alert(service.type()).status());
        assertEquals(AlertRuleTestSupport.BASE.plus(Duration.ofMinutes(270)),
                exactThreshold.alert(service.type()).startTime());
    }

    @Test
    void singleBreakResetsAtExactlyFortyFiveMinutesAndClosesAlert() {
        DeviceState state = AlertRuleTestSupport.state("FATIGUE-SINGLE");
        apply(state, packetAt(0, 60, true));
        apply(state, packetAt(270, 60, true));
        apply(state, packetAt(271, 0, false));
        apply(state, packetAt(316, 0, false));

        assertEquals(AlertStatus.CLOSED, state.alert(service.type()).status());
        assertEquals(AlertRuleTestSupport.BASE.plus(Duration.ofMinutes(316)),
                state.alert(service.type()).endTime());
    }

    @Test
    void splitBreakResetsAfterFifteenThenThirtyMinutesInOrder() {
        DeviceState state = AlertRuleTestSupport.state("FATIGUE-SPLIT");
        apply(state, packetAt(0, 60, true));
        apply(state, packetAt(270, 60, true));
        apply(state, packetAt(271, 0, false));
        apply(state, packetAt(286, 60, true));
        apply(state, packetAt(287, 60, true));
        apply(state, packetAt(288, 0, false));
        apply(state, packetAt(318, 60, true));

        assertEquals(AlertStatus.CLOSED, state.alert(service.type()).status());
        assertEquals(AlertRuleTestSupport.BASE.plus(Duration.ofMinutes(318)),
                state.alert(service.type()).endTime());
    }

    @Test
    void shortBreakAndThirtyBeforeFifteenDoNotResetDrivingTime() {
        DeviceState shortFirstBreak = AlertRuleTestSupport.state("FATIGUE-SHORT");
        apply(shortFirstBreak, packetAt(0, 60, true));
        apply(shortFirstBreak, packetAt(270, 0, false));
        apply(shortFirstBreak, packetAtSeconds(270 * 60L + 14 * 60L + 59, 60, true));
        apply(shortFirstBreak, packetAt(315, 0, false));
        apply(shortFirstBreak, packetAt(345, 60, true));
        assertEquals(AlertStatus.OPEN, shortFirstBreak.alert(service.type()).status());

        DeviceState wrongOrder = AlertRuleTestSupport.state("FATIGUE-ORDER");
        apply(wrongOrder, packetAt(0, 60, true));
        apply(wrongOrder, packetAt(270, 0, false));
        apply(wrongOrder, packetAt(300, 60, true));
        apply(wrongOrder, packetAt(301, 0, false));
        apply(wrongOrder, packetAt(316, 60, true));
        assertEquals(AlertStatus.OPEN, wrongOrder.alert(service.type()).status());
    }

    @Test
    void breakShorterThanFortyFiveMinutesDoesNotResetAndExactlyFiveKmhIsNotDriving() {
        DeviceState state = AlertRuleTestSupport.state("FATIGUE-44");
        apply(state, packetAt(0, 60, true));
        apply(state, packetAt(270, 0, false));
        apply(state, packetAtSeconds(270 * 60L + 44 * 60L + 59, 60, true));
        apply(state, packetAt(316, 60, true));
        assertEquals(AlertStatus.OPEN, state.alert(service.type()).status());

        DeviceState fiveKmh = AlertRuleTestSupport.state("FATIGUE-5");
        apply(fiveKmh, packetAt(0, 5, true));
        apply(fiveKmh, packetAt(300, 5, true));
        assertNull(fiveKmh.alert(service.type()));
    }

    @Test
    void firstSplitBreakMustReachFifteenMinutesAndSecondMustReachThirtyMinutes() {
        DeviceState firstBreakUnder = AlertRuleTestSupport.state("FATIGUE-FIRST-UNDER");
        apply(firstBreakUnder, packetAt(0, 60, true));
        apply(firstBreakUnder, packetAt(270, 0, false));
        apply(firstBreakUnder, packetAtSeconds(270 * 60L + 14 * 60L + 59, 60, true));
        apply(firstBreakUnder, packetAt(271, 0, false));
        apply(firstBreakUnder, packetAt(301, 60, true));
        assertEquals(AlertStatus.OPEN, firstBreakUnder.alert(service.type()).status());

        DeviceState secondBreakUnder = AlertRuleTestSupport.state("FATIGUE-SECOND-UNDER");
        apply(secondBreakUnder, packetAt(0, 60, true));
        apply(secondBreakUnder, packetAt(270, 0, false));
        apply(secondBreakUnder, packetAt(285, 60, true));
        apply(secondBreakUnder, packetAt(286, 0, false));
        apply(secondBreakUnder, packetAtSeconds(286 * 60L + 29 * 60L + 59, 60, true));
        assertEquals(AlertStatus.OPEN, secondBreakUnder.alert(service.type()).status());
    }

    private void apply(DeviceState state, Packet packet) {
        Packet devicePacket = new Packet(state.deviceId(), packet.timestamp(), packet.lat(),
                packet.lon(), packet.speedKmh(), packet.ignition());
        AlertRuleTestSupport.apply(service, state, devicePacket);
    }

    private Packet packetAt(long minutes, double speed, boolean ignition) {
        return packetAtSeconds(minutes * 60L, speed, ignition);
    }

    private Packet packetAtSeconds(long seconds, double speed, boolean ignition) {
        return AlertRuleTestSupport.packetAtSeconds("FATIGUE-TEST", seconds, speed, ignition);
    }
}
