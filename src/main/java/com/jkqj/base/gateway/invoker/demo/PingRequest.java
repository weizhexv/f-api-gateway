package com.jkqj.base.gateway.invoker.demo;

import lombok.Data;

@Data
public class PingRequest {
    private String name;
    private int age;
    private boolean isMale;
    private String[] hobbies;
}
