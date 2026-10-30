package in.rohanIT.dto;

import java.util.List;

public record IngestionResponse(
        int acceptedCount,
        int duplicateCount,
        int outOfOrderIgnoredCount,
        int rejectedCount,
        List<PacketRejection> rejections
) {
}
