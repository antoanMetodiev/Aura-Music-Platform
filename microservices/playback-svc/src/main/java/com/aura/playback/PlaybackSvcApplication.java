package com.aura.playback;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PlaybackSvcApplication {

	public static void main(String[] args) {
		SpringApplication.run(PlaybackSvcApplication.class, args);
	}

}
