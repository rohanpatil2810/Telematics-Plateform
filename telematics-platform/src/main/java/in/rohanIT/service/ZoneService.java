package in.rohanIT.service;

import in.rohanIT.model.Packet;
import in.rohanIT.interfaces.SpeedLimitResolver;
import in.rohanIT.model.SpeedLimit;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import tools.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class ZoneService implements SpeedLimitResolver {
    private static final double BOUNDARY_EPSILON = 1.0e-10;
    private final double defaultLimit;
    private final List<Zone> zones;

    public ZoneService(ObjectMapper objectMapper) {
        try (InputStream input = new ClassPathResource("data/zones.json").getInputStream()) {
            ZoneFile zoneFile = objectMapper.readValue(input, ZoneFile.class);
            defaultLimit = zoneFile.defaultSpeedLimitKmh();
            zones = List.copyOf(zoneFile.zones());
            if (defaultLimit < 0 || zones.stream().anyMatch(zone ->
                    zone.speedLimitKmh() < 0 || zone.polygon().size() < 3)) {
                throw new IllegalStateException("Zone configuration contains invalid limits or polygons");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load data/zones.json", exception);
        }
    }

    @Override
    public SpeedLimit limitAt(Packet packet) {
        return zones.stream()
                .filter(zone -> contains(zone.polygon(), packet.lat(), packet.lon()))
                .min((left, right) -> Double.compare(left.speedLimitKmh(), right.speedLimitKmh()))
                .map(zone -> new SpeedLimit(zone.zoneId(), zone.speedLimitKmh()))
                .orElseGet(() -> new SpeedLimit("DEFAULT", defaultLimit));
    }

    private boolean contains(List<List<Double>> polygon, double lat, double lon) {
        boolean inside = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            double lat1 = polygon.get(j).get(0);
            double lon1 = polygon.get(j).get(1);
            double lat2 = polygon.get(i).get(0);
            double lon2 = polygon.get(i).get(1);
            if (onSegment(lat, lon, lat1, lon1, lat2, lon2)) {
                return true;
            }
            boolean crossesLatitude = (lat1 > lat) != (lat2 > lat);
            if (crossesLatitude) {
                double crossingLon = (lon2 - lon1) * (lat - lat1) / (lat2 - lat1) + lon1;
                if (lon < crossingLon) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    private boolean onSegment(double lat, double lon, double lat1, double lon1,
                              double lat2, double lon2) {
        double cross = (lon - lon1) * (lat2 - lat1) - (lat - lat1) * (lon2 - lon1);
        if (Math.abs(cross) > BOUNDARY_EPSILON) {
            return false;
        }
        return lat >= Math.min(lat1, lat2) - BOUNDARY_EPSILON
                && lat <= Math.max(lat1, lat2) + BOUNDARY_EPSILON
                && lon >= Math.min(lon1, lon2) - BOUNDARY_EPSILON
                && lon <= Math.max(lon1, lon2) + BOUNDARY_EPSILON;
    }

    private record ZoneFile(double defaultSpeedLimitKmh, List<Zone> zones) {}
    private record Zone(String zoneId, String name, double speedLimitKmh, List<List<Double>> polygon) {}
}
