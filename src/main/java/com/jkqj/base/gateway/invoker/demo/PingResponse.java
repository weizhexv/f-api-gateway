package com.jkqj.base.gateway.invoker.demo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;

@Data
@AllArgsConstructor
public class PingResponse {
    private String name;
    private String[] hobbies;
    private LocalDateTime localDateTime;
    private Date date;
    private LocalDate localDate;
    private LocalTime localTime;
    private Address address;

    @Data
    @AllArgsConstructor
    public static class Address {
       private String city;
       private String country;
    }
}
