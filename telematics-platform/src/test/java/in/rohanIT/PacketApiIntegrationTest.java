package in.rohanIT;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PacketApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void testClockIngestionAndAlertFilteringWorkTogether() throws Exception {
        mockMvc.perform(put("/api/v1/test/clock")
                        .contentType("application/json")
                        .content("""
                                {"now":"2026-10-26T09:00:00Z"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.now").value("2026-10-26T09:00:00Z"));

        mockMvc.perform(post("/api/v1/packets")
                        .contentType("application/json")
                        .content("""
                                [
                                  {"deviceId":"API-OVER","timestamp":"2026-10-26T09:00:00Z","lat":23.01,"lon":72.555,"speedKmh":50,"ignition":true},
                                  {"deviceId":"API-OVER","timestamp":"2026-10-26T09:00:15Z","lat":23.01,"lon":72.555,"speedKmh":50,"ignition":true},
                                  {"deviceId":"API-OVER","timestamp":"2026-10-26T09:00:30Z","lat":23.01,"lon":72.555,"speedKmh":50,"ignition":true}
                                ]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedCount").value(3))
                .andExpect(jsonPath("$.rejectedCount").value(0));

        mockMvc.perform(get("/api/v1/alerts").param("deviceId", "API-OVER").param("type", "OVERSPEED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].details.worstZoneId").value("SCHOOL-DPS"));
    }

    @Test
    void openApiDocumentsPacketAndAlertEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Telematics Alert Engine API")))
                .andExpect(content().string(containsString("/api/v1/packets")))
                .andExpect(content().string(containsString("/api/v1/alerts")))
                .andExpect(content().string(containsString("PacketRequest")));
    }

    @Test
    void packetEndpointRejectsNonArrayRequestBody() throws Exception {
        mockMvc.perform(post("/api/v1/packets")
                        .contentType("application/json")
                        .content("""
                                {"deviceId":"NOT-AN-ARRAY"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Request body must be a JSON array of packets"));
    }

    @Test
    void packetEndpointRejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/packets")
                        .contentType("application/json")
                        .content("[{\"deviceId\":]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Request body or query parameter is invalid"));
    }

    @Test
    void injectedTestClockOpensOfflineAlertAtInclusiveThreshold() throws Exception {
        mockMvc.perform(put("/api/v1/test/clock")
                        .contentType("application/json")
                        .content("""
                                {"now":"2026-10-27T10:00:00Z"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/packets")
                        .contentType("application/json")
                        .content("""
                                [{"deviceId":"API-OFFLINE","timestamp":"2026-10-27T10:00:00Z",
                                  "lat":23.03,"lon":72.55,"speedKmh":40,"ignition":true}]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedCount").value(1));

        mockMvc.perform(put("/api/v1/test/clock")
                        .contentType("application/json")
                        .content("""
                                {"now":"2026-10-27T10:15:00Z"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/alerts").param("deviceId", "API-OFFLINE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].startTime").value("2026-10-27T10:00:00Z"));
    }
}
