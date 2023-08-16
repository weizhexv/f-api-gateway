package com.jkqj.base.gateway.middleware;

import com.jkqj.base.gateway.handler.BaseHandler;
import com.jkqj.base.gateway.router.Context;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

@Slf4j
public class HeaderMiddleware extends BaseHandler implements Middleware {

    @Override
    public boolean preProcess(Context context) {
        var route = context.getRoute();
        processHeaders(route.getPassHeaders(), context);
        return true;
    }

    @Override
    public Middlewares getType() {
        return Middlewares.HEADERS;
    }

    private void processHeaders(Set<String> passHeaders, Context context) {
        if (passHeaders == null || passHeaders.isEmpty()) {
            return;
        }

        var request = context.getRequest();
        for (var header : passHeaders) {
            if (request.getHeader(header) != null) {
                context.addHeader(header, request.getHeaders(header));
                log.debug("added header {}", header);
            }
        }
    }
}
