package com.jkqj.base.gateway.config;

import com.google.common.base.Strings;
import lombok.Data;

import static com.google.common.base.Preconditions.checkArgument;

@Data
public class ProxyPassProperties {
    public static final String PROTOCOL_SEP = "://";
    public static final String PATH_SEP = ":";

    private String upstream;
    private String path;

    public void validate() {
        checkArgument(!Strings.isNullOrEmpty(upstream));
        checkArgument(!Strings.isNullOrEmpty(path));
    }
}
