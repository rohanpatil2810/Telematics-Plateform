package in.rohanIT.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AlertProperties.class)
public class ApplicationConfiguration {
}
