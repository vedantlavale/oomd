package com.vsms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VsmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(VsmsApplication.class, args);
    }
}
