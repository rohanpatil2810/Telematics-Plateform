package in.rohanIT.dto;

public record PacketRejection(int packetIndex, String deviceId, String timestamp, String reason) {
}
