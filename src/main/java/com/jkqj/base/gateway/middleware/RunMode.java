package com.jkqj.base.gateway.middleware;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

/**
 * @author xuweizhe@reta-inc.com
 * @date 2022/5/3
 * @description
 */
public enum RunMode {
    READ,
    WRITE;

    public static RunMode of(String runMode) {
        return Arrays.stream(RunMode.values())
                .filter(item -> StringUtils.equalsIgnoreCase(runMode, item.name()))
                .findFirst().orElseThrow(IllegalArgumentException::new);
    }
}
