package in.rohanIT.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class ClockConfiguration {
    @Bean
    @Profile("!test")
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
