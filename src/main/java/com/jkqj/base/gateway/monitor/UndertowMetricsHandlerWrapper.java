package com.jkqj.base.gateway.monitor;

import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.MetricsHandler;
import lombok.Getter;
import org.springframework.stereotype.Component;

/**
 * @author xuweizhe@reta-inc.com
 * @date 2022/2/14
 * @description
 */
@Component
public class UndertowMetricsHandlerWrapper implements HandlerWrapper {
    @Getter
    private MetricsHandler metricsHandler;

    @Override
    public HttpHandler wrap(HttpHandler httpHandler) {
        metricsHandler = new MetricsHandler(httpHandler);
        return metricsHandler;
    }
}
