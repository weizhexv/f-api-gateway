package com.jkqj.base.gateway.config;

import com.google.common.base.Strings;
import lombok.Data;

import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

@Data
public class RuleProperties {
    private String path;
    private List<String> methods;
    private String description;
    private Boolean login = true;
    private List<String> roles;
    private List<String> permissions;
    private Set<String> passHeaders;
    private String traceHeader;
    private String proxyPass;
    private Integer timeout;

    public void validate() {
        checkArgument(!Strings.isNullOrEmpty(path));
        checkArgument(methods != null && !methods.isEmpty());
        checkArgument(!Strings.isNullOrEmpty(proxyPass));
    }
}
