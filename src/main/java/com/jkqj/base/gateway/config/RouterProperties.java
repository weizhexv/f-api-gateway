package com.jkqj.base.gateway.config;

import lombok.Data;

import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

@Data
public class RouterProperties {
    private List<RuleProperties> rules;
    private Set<String> passHeaders;
    private Set<String> ruleDataIds;
    private Set<String> sharingPaths;
    private Set<String> guestPaths;
    private List<UpstreamProperties> upstreams;

    public void validate() {
        checkArgument(rules != null && !rules.isEmpty());
        rules.forEach(RuleProperties::validate);
    }

}
