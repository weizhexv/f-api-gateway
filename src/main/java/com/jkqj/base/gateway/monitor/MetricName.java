package com.jkqj.base.gateway.monitor;

/**
 * @author xuweizhe@reta-inc.com
 * @date 2022/2/15
 * @description
 */
public interface MetricName {
    String request = "jkqj_gateway_request_timer";

    String exception = "jkqj_gateway_exception_counter";

    String upstreamRequest = "jkqj_gateway_upstream_request_timer";
}
