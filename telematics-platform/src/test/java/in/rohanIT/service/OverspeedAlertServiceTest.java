package in.rohanIT.service;

import in.rohanIT.model.Alert;
import in.rohanIT.model.AlertStatus;
import in.rohanIT.model.DeviceState;
import in.rohanIT.model.Packet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverspeedAlertServiceTest {
    private OverspeedAlertService service;

    @BeforeEach
    void setUp() {
        service = new OverspeedAlertService(new ZoneService(new ObjectMapper()),
                AlertRuleTestSupport.properties());
    }

    @Test
    void opensAtThirtySecondsAndUsesLowestOverlappingZone() {
        DeviceState state = AlertRuleTestSupport.state("V-OPEN");
        apply(state, packetAt(0, 50));
        apply(state, packetAt(15, 55));
        apply(state, packetAt(30, 60));

        Alert alert = state.alert(service.type());
        assertEquals(AlertStatus.OPEN, alert.status());
        assertEquals(AlertRuleTestSupport.BASE, alert.startTime());
        assertEquals("SCHOOL-DPS", alert.details().get("worstZoneId"));
        assertEquals(30.0, alert.details().get("worstExcessKmh"));
        assertEquals(30.0, alert.details().get("limitAtWorstKmh"));
        assertEquals(60.0, alert.details().get("maxSpeedKmh"));
    }

    @Test
    void violationAtTwentyNineSecondsDoesNotOpenButExactThirtyDoes() {
        DeviceState shortRun = AlertRuleTestSupport.state("V-SHORT");
        apply(shortRun, packetAt(0, 40));
        apply(shortRun, packetAt(29, 40));
        assertNull(shortRun.alert(service.type()));

        DeviceState exactRun = AlertRuleTestSupport.state("V-EXACT");
        apply(exactRun, packetAt(0, 40));
        apply(exactRun, packetAt(30, 40));
        assertEquals(AlertStatus.OPEN, exactRun.alert(service.type()).status());
    }

    @Test
    void speedAtLimitIsCompliantAndExactlyFivePercentOverIsNeither() {
        DeviceState atLimit = AlertRuleTestSupport.state("V-LIMIT");
        apply(atLimit, packetAt(0, 30));
        apply(atLimit, packetAt(30, 30));
        assertNull(atLimit.alert(service.type()));

        DeviceState toleranceBoundary = AlertRuleTestSupport.state("V-NEITHER");
        apply(toleranceBoundary, packetAt(0, 31.5));
        apply(toleranceBoundary, packetAt(30, 31.5));
        assertNull(toleranceBoundary.alert(service.type()));

        DeviceState justOver = AlertRuleTestSupport.state("V-OVER-LIMIT");
        apply(justOver, packetAt(0, 31.5001));
        apply(justOver, packetAt(30, 31.5001));
        assertEquals(AlertStatus.OPEN, justOver.alert(service.type()).status());
    }

    @Test
    void compliantRunMustReachFifteenSecondsAndEndsAtItsFirstPacket() {
        DeviceState tooShort = AlertRuleTestSupport.state("V-CLOSE-SHORT");
        openAlert(tooShort);
        apply(tooShort, packetAt(35, 30));
        apply(tooShort, packetAt(49, 30));
        assertEquals(AlertStatus.OPEN, tooShort.alert(service.type()).status());

        DeviceState exact = AlertRuleTestSupport.state("V-CLOSE-EXACT");
        openAlert(exact);
        apply(exact, packetAt(35, 30));
        apply(exact, packetAt(50, 30));
        assertEquals(AlertStatus.CLOSED, exact.alert(service.type()).status());
        assertEquals(AlertRuleTestSupport.BASE.plusSeconds(35), exact.alert(service.type()).endTime());
    }

    @Test
    void compliantPacketsDuringClosingWindowContributeToAlertDetails() {
        DeviceState state = AlertRuleTestSupport.state("V-CLOSE-DETAILS");
        openAlert(state);
        apply(state, highwayPacket(35, 70));

        Alert stillOpen = state.alert(service.type());
        assertEquals(AlertStatus.OPEN, stillOpen.status());
        assertEquals(70.0, stillOpen.details().get("maxSpeedKmh"));

        apply(state, highwayPacket(50, 70));

        Alert closed = state.alert(service.type());
        assertEquals(AlertStatus.CLOSED, closed.status());
        assertEquals(AlertRuleTestSupport.BASE.plusSeconds(35), closed.endTime());
        assertEquals(70.0, closed.details().get("maxSpeedKmh"));
    }

    @Test
    void neitherInterruptsAClosingRunAndZoneChangesDoNotSplitViolation() {
        DeviceState state = AlertRuleTestSupport.state("V-ZONES");
        apply(state, packetAt(0, 50));
        apply(state, highwayPacket(20, 110));
        apply(state, cityPacket(30, 70));
        assertEquals(AlertStatus.OPEN, state.alert(service.type()).status());
        assertEquals(1, state.alerts().size());

        apply(state, packetAt(35, 30));
        apply(state, packetAt(45, 31));
        apply(state, packetAt(50, 30));
        assertEquals(AlertStatus.OPEN, state.alert(service.type()).status());
    }

    @Test
    void newViolationAfterClosureCreatesANewLiveAlert() {
        DeviceState state = AlertRuleTestSupport.state("V-REOPEN");
        openAlert(state);
        apply(state, packetAt(35, 30));
        apply(state, packetAt(50, 30));
        String oldId = state.alert(service.type()).alertId();

        apply(state, packetAt(60, 40));
        apply(state, packetAt(90, 40));

        assertEquals(AlertStatus.OPEN, state.alert(service.type()).status());
        assertEquals(AlertRuleTestSupport.BASE.plusSeconds(60), state.alert(service.type()).startTime());
        assertTrue(!oldId.equals(state.alert(service.type()).alertId()));
        assertEquals(1, state.alerts().size());
    }

    private void openAlert(DeviceState state) {
        apply(state, packetAt(0, 40));
        apply(state, packetAt(30, 40));
    }

    private void apply(DeviceState state, Packet packet) {
        Packet devicePacket = new Packet(state.deviceId(), packet.timestamp(), packet.lat(),
                packet.lon(), packet.speedKmh(), packet.ignition());
        AlertRuleTestSupport.apply(service, state, devicePacket);
    }

    private Packet packetAt(long seconds, double speed) {
        return AlertRuleTestSupport.packetAtSeconds("V-TEST", seconds, speed, true);
    }

    private Packet highwayPacket(long seconds, double speed) {
        return AlertRuleTestSupport.packet("V-ZONES", AlertRuleTestSupport.BASE.plusSeconds(seconds),
                23.01, 72.52, speed, true);
    }

    private Packet cityPacket(long seconds, double speed) {
        return AlertRuleTestSupport.packet("V-ZONES", AlertRuleTestSupport.BASE.plusSeconds(seconds),
                23.07, 72.55, speed, true);
    }
}
