package com.jkqj.base.gateway.endpoint;

import com.jkqj.base.gateway.router.Context;

public interface Endpoint {
    void handle(String uri, Context context);

    void preProcess(String uri, Context context);

    void postProcess(String uri, Context context);

    default void doHandle(String uri, Context context) {
        preProcess(uri, context);
        handle(uri, context);
        postProcess(uri, context);
    }
}
