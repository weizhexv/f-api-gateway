package com.jkqj.base.gateway.upstream;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class HttpUpstream extends Upstream {
    private String host;
    private Integer port;

    @Override
    public Upstreams getType() {
        return Upstreams.HTTP;
    }
}
