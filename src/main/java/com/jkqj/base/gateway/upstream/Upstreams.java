package com.jkqj.base.gateway.upstream;

public enum Upstreams {
    HTTP, DUBBO;

    public String prefix() {
        return name().toLowerCase() + ":";
    }
}
