package com.jkqj.base.gateway.config;

import com.google.common.base.Strings;
import lombok.Data;

import static com.google.common.base.Preconditions.checkArgument;

@Data
public class UpstreamProperties {
    private String id;
    private String type;
    private String host;
    private Integer port = 80;

    public void validate() {
        checkArgument(!Strings.isNullOrEmpty(id));
        checkArgument(!Strings.isNullOrEmpty(type));
        checkArgument(!Strings.isNullOrEmpty(host));
        checkArgument(port != null && port > 0);
    }
}
