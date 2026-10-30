package in.rohanIT.controller;

import in.rohanIT.dto.AlertResponse;
import in.rohanIT.dto.IngestionResponse;
import in.rohanIT.dto.PacketRequest;
import in.rohanIT.model.AlertType;
import in.rohanIT.service.PacketIngestionService;
import java.util.List;
import tools.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Packet and alert operations")
public class PacketController {
    private final PacketIngestionService ingestionService;

    public PacketController(PacketIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/packets")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Ingest a batch of GPS packets")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "An array of GPS packets. Invalid items are rejected individually.",
            required = true,
            content = @io.swagger.v3.oas.annotations.media.Content(
                    array = @io.swagger.v3.oas.annotations.media.ArraySchema(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    implementation = PacketRequest.class))))
    public IngestionResponse ingest(@RequestBody JsonNode request) {
        if (request == null || !request.isArray()) {
            throw new IllegalArgumentException("Request body must be a JSON array of packets");
        }
        return ingestionService.ingest(request);
    }

    @GetMapping("/alerts")
    @Operation(summary = "List the latest alert lifecycle state per device and alert type")
    public List<AlertResponse> alerts(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) AlertType type
    ) {
        return ingestionService.alerts(deviceId, type).stream()
                .map(AlertResponse::from)
                .toList();
    }
}
