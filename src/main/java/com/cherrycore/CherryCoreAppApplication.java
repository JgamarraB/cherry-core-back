package com.cherrycore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CherryCoreAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(CherryCoreAppApplication.class, args);
	}

}
