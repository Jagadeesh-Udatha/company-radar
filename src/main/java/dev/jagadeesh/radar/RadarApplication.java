package dev.jagadeesh.radar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RadarApplication {

    public static void main(String[] args) {
        SpringApplication.run(RadarApplication.class, args);
    }
}
