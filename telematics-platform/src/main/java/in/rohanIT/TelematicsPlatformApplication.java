package in.rohanIT;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@SpringBootApplication
@EnableScheduling
@OpenAPIDefinition(info = @Info(
		title = "Telematics Alert Engine API",
		version = "v1",
		description = "Packet ingestion and live vehicle alert lifecycle APIs"
))
public class TelematicsPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(TelematicsPlatformApplication.class, args);
	}

}
