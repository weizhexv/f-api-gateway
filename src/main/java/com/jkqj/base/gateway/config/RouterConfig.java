package com.jkqj.base.gateway.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@ToString
@Slf4j
@AllArgsConstructor
public class RouterConfig {
    @Getter
    @Setter
    private RouterProperties properties;

    public void validate() {
        log.debug("validating properties \n{}", properties);
        properties.validate();
    }
}
