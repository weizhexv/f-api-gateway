package com.jkqj.base.gateway.middleware;

import com.jkqj.base.gateway.handler.BaseHandler;
import com.jkqj.base.gateway.router.Context;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.rpc.RpcContext;

@Slf4j
public class TimeoutMiddleware extends BaseHandler implements Middleware {
    private static final String KEY_TIMEOUT = "timeout";

    @Override
    public Middlewares getType() {
        return Middlewares.TIMEOUT;
    }

    @Override
    public boolean preProcess(Context context) {
        var timeout = context.getRoute().getTimeout();
        processTimeout(timeout);
        return true;
    }

    private void processTimeout(Integer timeout) {
        if (timeout == null || timeout <= 0) {
            return;
        }

        log.info("TimeoutMiddleware set object attachment {}={}", KEY_TIMEOUT, timeout);
        RpcContext.getClientAttachment().setObjectAttachment(KEY_TIMEOUT, timeout);
    }
}
