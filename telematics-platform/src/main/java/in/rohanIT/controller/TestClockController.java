package in.rohanIT.controller;

import in.rohanIT.config.AdjustableClock;
import in.rohanIT.dto.ClockUpdateRequest;
import in.rohanIT.dto.ClockUpdateResponse;
import in.rohanIT.service.PacketIngestionService;
import java.time.Instant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/test/clock")
@Profile("test")
@Tag(name = "Test utilities")
public class TestClockController {
    private final AdjustableClock clock;
    private final PacketIngestionService ingestionService;

    public TestClockController(AdjustableClock clock, PacketIngestionService ingestionService) {
        this.clock = clock;
        this.ingestionService = ingestionService;
    }

    @PutMapping
    @Operation(summary = "Set the test clock and run the offline alert check")
    public ClockUpdateResponse setClock(@RequestBody ClockUpdateRequest request) {
        if (request.now() == null || request.now().isBlank()) {
            throw new IllegalArgumentException("now is required and must be an ISO-8601 instant");
        }
        Instant now;
        try {
            now = Instant.parse(request.now());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("now must be a valid ISO-8601 instant in UTC");
        }
        clock.setInstant(now);
        ingestionService.checkOfflineAlerts();
        return new ClockUpdateResponse(now, "Test clock updated and offline alerts checked");
    }
}
