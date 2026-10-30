package in.rohanIT.dto;

import java.time.Instant;

public record ClockUpdateResponse(Instant now, String message) {
}
