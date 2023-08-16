package com.jkqj.base.gateway.invoker;

import com.google.common.collect.Maps;
import com.jkqj.base.gateway.monitor.MetricHelper;
import com.jkqj.base.gateway.monitor.MetricName;
import com.jkqj.base.gateway.monitor.MetricTags;
import com.jkqj.base.gateway.router.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static java.util.Objects.isNull;

public interface Invoker {
    Logger log = LoggerFactory.getLogger(Invoker.class);

    void invoke(Context context);

    default void preProcess(Context context) {
        context.setUpstreamStartAt(System.currentTimeMillis());
    }

    default void postProcess(Context context) {
        try {
            var metricTags = MetricTags.of(context);
            var cost = System.currentTimeMillis() - context.getUpstreamStartAt();
            MetricHelper.record(MetricName.upstreamRequest, cost, metricTags);
        } catch (Throwable th) {
            log.error("Invoker.postProcess error", th);
        }
    }

    default void doInvoke(Context context) {
        preProcess(context);
        invoke(context);
        postProcess(context);
    }

    default Map<String, List<String>> setContentType(Map<String, List<String>> headers) {
        if (isNull(headers)) {
            headers = Maps.newHashMap();
        }
        headers.put("Content-Type", List.of("application/json;charset=utf-8"));
        return headers;
    }
}
