package in.rohanIT.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OfflineCheckScheduler {
    private final PacketIngestionService ingestionService;

    public OfflineCheckScheduler(PacketIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Scheduled(fixedDelayString = "${alerts.offline.check-interval-ms:60000}")
    public void checkOfflineDevices() {
        ingestionService.checkOfflineAlerts();
    }
}
