package com.shimanamisan.baseballmarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.shimanamisan.baseballmarket")
public class BaseballMarketSpringApplication {

    public static void main(String[] args) {
        SpringApplication.run(BaseballMarketSpringApplication.class, args);
    }
}
