package in.rohanIT.service;

import in.rohanIT.config.AlertProperties;
import in.rohanIT.model.Packet;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketValidatorTest {
    private static final Instant SERVER_TIME = Instant.parse("2026-10-26T09:00:00Z");
    private static final String VALID_PACKET = """
            {"deviceId":"V1","timestamp":"2026-10-26T09:00:00Z",
             "lat":23.0,"lon":72.0,"speedKmh":10.0,"ignition":true}
            """;

    private final ObjectMapper mapper = new ObjectMapper();
    private PacketValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PacketValidator(AlertRuleTestSupport.properties());
    }

    @Test
    void rejectsMissingOrBlankDeviceIdAndMissingOrInvalidTimestamp() {
        assertRejected(VALID_PACKET.replace("\"deviceId\":\"V1\",", ""), "deviceId");
        assertRejected(VALID_PACKET.replace("\"deviceId\":\"V1\"", "\"deviceId\":\"  \""), "deviceId");
        assertRejected(VALID_PACKET.replace("\"timestamp\":\"2026-10-26T09:00:00Z\",", ""), "timestamp");
        assertRejected(VALID_PACKET.replace("2026-10-26T09:00:00Z", "not-an-instant"), "timestamp");
    }

    @Test
    void rejectsEachMissingRequiredPacketField() {
        assertRejected(VALID_PACKET.replace("\"lat\":23.0,", ""), "lat");
        assertRejected(VALID_PACKET.replace("\"lon\":72.0,", ""), "lon");
        assertRejected(VALID_PACKET.replace("\"speedKmh\":10.0,", ""), "speedKmh");
        assertRejected(VALID_PACKET.replace(",\"ignition\":true", ""), "ignition");
        assertRejected(VALID_PACKET.replace("\"ignition\":true", "\"ignition\":\"true\""), "ignition");
    }

    @Test
    void enforcesCoordinateRangesAndAcceptsTheirInclusiveBoundaries() throws Exception {
        assertRejected(VALID_PACKET.replace("\"lat\":23.0", "\"lat\":90.0001"), "lat");
        assertRejected(VALID_PACKET.replace("\"lat\":23.0", "\"lat\":-90.0001"), "lat");
        assertRejected(VALID_PACKET.replace("\"lon\":72.0", "\"lon\":180.0001"), "lon");
        assertRejected(VALID_PACKET.replace("\"lon\":72.0", "\"lon\":-180.0001"), "lon");

        Packet northEast = validate(VALID_PACKET
                .replace("\"lat\":23.0", "\"lat\":90.0")
                .replace("\"lon\":72.0", "\"lon\":180.0"));
        Packet southWest = validate(VALID_PACKET
                .replace("\"lat\":23.0", "\"lat\":-90.0")
                .replace("\"lon\":72.0", "\"lon\":-180.0"));
        assertEquals(90.0, northEast.lat());
        assertEquals(-180.0, southWest.lon());
    }

    @Test
    void rejectsNegativeAndNonFiniteSpeeds() {
        assertRejected(VALID_PACKET.replace("\"speedKmh\":10.0", "\"speedKmh\":-0.1"), "speedKmh");
        assertRejected(VALID_PACKET.replace("\"speedKmh\":10.0", "\"speedKmh\":1e309"), "finite");
    }

    @Test
    void futureTimestampToleranceIsInclusive() throws Exception {
        String justInside = VALID_PACKET.replace(
                "2026-10-26T09:00:00Z", "2026-10-26T09:01:00Z");
        String justOutside = VALID_PACKET.replace(
                "2026-10-26T09:00:00Z", "2026-10-26T09:01:00.000000001Z");

        assertEquals(Instant.parse("2026-10-26T09:01:00Z"),
                validate(justInside).timestamp());
        assertRejected(justOutside, "future");
    }

    private Packet validate(String json) throws Exception {
        return validator.validate(mapper.readTree(json), SERVER_TIME);
    }

    private void assertRejected(String json, String expectedMessage) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> validator.validate(mapper.readTree(json), SERVER_TIME));
        assertTrue(exception.getMessage().contains(expectedMessage), exception.getMessage());
    }
}
