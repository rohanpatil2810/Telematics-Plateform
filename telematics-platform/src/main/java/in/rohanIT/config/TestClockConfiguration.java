package in.rohanIT.config;

import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class TestClockConfiguration {
    @Bean
    AdjustableClock adjustableClock() {
        return new AdjustableClock(Instant.EPOCH, ZoneOffset.UTC);
    }
}
