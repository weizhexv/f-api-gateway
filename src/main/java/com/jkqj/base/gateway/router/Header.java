package com.jkqj.base.gateway.router;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class Header {
    private String name;
    private Object value;

    public Header(String name, Object value) {
        this.name = name;
        this.value = value;
    }

    public String getValueString() {
        return value == null ? StringUtils.EMPTY : value.toString();
    }
}
