package com.jkqj.base.gateway.middleware;

import com.jkqj.base.gateway.handler.Handler;

public interface Middleware extends Handler, Comparable<Middleware> {
    Middlewares getType();

    @Override
    default int compareTo(Middleware that) {
        return this.getType().ordinal() - that.getType().ordinal();
    }
}
