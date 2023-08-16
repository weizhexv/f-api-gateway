package com.jkqj.base.gateway.router;

import com.jkqj.base.gateway.upstream.Upstream;
import lombok.Data;


@Data
public class ProxyPass {
    private Upstream upstream;
    private String uri;

}
