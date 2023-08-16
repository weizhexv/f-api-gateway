package com.jkqj.base.gateway.middleware;

import com.jkqj.base.gateway.handler.BaseHandler;
import com.jkqj.base.gateway.router.Context;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

import java.util.UUID;

@Slf4j
public class TraceMiddleware extends BaseHandler implements Middleware {
    public static final String TRACE_ID = "trace-id";

    @Override
    public boolean preProcess(Context context) {
        var route = context.getRoute();
        if (route.getTraceHeader() == null) {
            log.debug("trace is off");
            return true;
        }

        var request = context.getRequest();
        var traceId = request.getHeader(route.getTraceHeader());
        if (StringUtils.isBlank(traceId)) {
            traceId = request.getHeader(TRACE_ID);
        }
        if (StringUtils.isBlank(traceId)) {
            traceId = UUID.randomUUID().toString();
            log.debug("generate traceId {}", traceId);
        }
        context.setTraceId(traceId);
        MDC.put(TRACE_ID, traceId);

        log.debug("set trace id {}", traceId);
        return true;
    }

    @Override
    public Middlewares getType() {
        return Middlewares.TRACE_ID;
    }
}
