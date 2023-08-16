package com.jkqj.base.gateway.monitor;

import com.jkqj.common.utils.JsonUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

/**
 * @author xuweizhe@reta-inc.com
 * @date 2022/2/15
 * @description
 */
@Component
@Slf4j
public class MetricHelper {
    private static MetricHelper instance;
    @Autowired
    MeterRegistry meterRegistry;

    @PostConstruct
    public void init() {
        instance = this;
    }

    public static void record(String name, MetricTags metricTags) {
        var tagList = MetricTags.toList(metricTags);
        var counter = Counter.builder(name).tags(tagList).register(instance.meterRegistry);
        counter.increment();
        log.debug("record metric, name:{}, tags:{}", name, JsonUtils.toJson(metricTags));
    }

    public static void record(String name, Long cost, MetricTags metricTags) {
        var tagList = MetricTags.toList(metricTags);
        var timer = Timer.builder(name).tags(tagList).register(instance.meterRegistry);
        timer.record(cost, TimeUnit.MILLISECONDS);
        log.debug("record metric, name:{}, cost:{}, tags:{}", name, cost, JsonUtils.toJson(metricTags));
    }
}
