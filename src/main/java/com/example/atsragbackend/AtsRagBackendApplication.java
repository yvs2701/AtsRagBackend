package com.example.atsragbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AtsRagBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AtsRagBackendApplication.class, args);
    }

}
