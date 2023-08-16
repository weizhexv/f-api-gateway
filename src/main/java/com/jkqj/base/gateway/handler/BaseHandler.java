package com.jkqj.base.gateway.handler;

import com.jkqj.base.gateway.router.Context;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BaseHandler implements Handler {
    protected Handler nextHandler;

    @Override
    public Handler next() {
        return nextHandler;
    }

    public void next(Handler handler) {
        nextHandler = handler;
    }

    @Override
    public void handle(Context context) {
        log.debug("invoking handler {} on {}"
                , this.getClass().getName(), context.getUri());

        Handler.super.handle(context);
    }
}
