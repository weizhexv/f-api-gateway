package com.jkqj.base.gateway.handler;

import com.jkqj.base.gateway.router.Context;

public interface Handler {

    default void handle(Context context) {
        if (preProcess(context)) {
            if (next() != null) {
                next().handle(context);
            }
        }

        postProcess(context);
    }

    default boolean preProcess(Context context) {
        return true;
    }

    default void postProcess(Context context) {
    }

    void next(Handler nextHandler);

    Handler next();
}
