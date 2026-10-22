package com.stockflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StockFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockFlowApplication.class, args);
    }
}
