package com.jkqj.base.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        var application = new SpringApplication(GatewayApplication.class);
        application.run(args);
    }
}

