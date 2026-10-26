package com.mpesa.fakebank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties
public class FakeBankApplication {
    public static void main(String[] args) {
        SpringApplication.run(FakeBankApplication.class, args);
    }
}
