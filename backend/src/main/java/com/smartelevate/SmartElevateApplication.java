package com.smartelevate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SmartElevateApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartElevateApplication.class, args);
    }
}
