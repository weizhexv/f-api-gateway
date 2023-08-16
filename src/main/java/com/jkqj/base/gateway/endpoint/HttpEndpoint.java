package com.jkqj.base.gateway.endpoint;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jkqj.base.gateway.middleware.TraceMiddleware;
import com.jkqj.base.gateway.monitor.MetricHelper;
import com.jkqj.base.gateway.monitor.MetricName;
import com.jkqj.base.gateway.monitor.MetricTags;
import com.jkqj.base.gateway.router.Context;
import com.jkqj.base.gateway.router.ProxyResult;
import com.jkqj.base.gateway.router.RouterHelper;
import com.jkqj.common.result.Result;
import com.jkqj.common.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.dubbo.rpc.RpcContext;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Controller
@Slf4j
public class HttpEndpoint implements Endpoint {
    @Autowired
    private RouterHelper routerHelper;

    @Autowired
    ObjectMapper objectMapper;

    private final Result<Object> defaultFailResult = Result.fail(1, "系统异常");

    @RequestMapping("/**/*")
    public void entry(HttpServletRequest request, HttpServletResponse response) {
        var context = new Context(request, response);
        doHandle(request.getRequestURI(), context);
    }

    @GetMapping("/ping")
    @ResponseBody
    public Result<Map<String, Object>> ping() {
        return Result.success(Map.of("name", "adam", "time", LocalDateTime.now()));
    }

    @Override
    public void handle(String uri, Context context) {
        try {
            routerHelper.get().handle(uri, context);
        } catch (Throwable t) {

            log.error("caught exception {}", ExceptionUtils.getStackTrace(t));
            context.fail(1, "router handle error", t);
            processError(context);
            return;
        }

        var result = context.getResult();
        if (result != null && result.isSuccess()) {
            processResponse(context.getResponse(), result);
        } else {
            Result<Object> failResult = defaultFailResult;
            if (result != null) {
                failResult = Result.fail(result.getCode(), result.getMessage());
            }
            renderError(context.getResponse(), failResult);
        }
    }

    @Override
    public void preProcess(String uri, Context context) {
        context.setEndpointStartAt(System.currentTimeMillis());

        //提前处理traceId,避免一个请求traceId不相同
        var request = context.getRequest();
        var traceId = request.getHeader(TraceMiddleware.TRACE_ID);
        if (StringUtils.isBlank(traceId)) {
            //客户端没传traceId，生成一个
            traceId = UUID.randomUUID().toString();
        }
        MDC.put(TraceMiddleware.TRACE_ID, traceId);
        context.setTraceId(traceId);

        //入口处日志
        log.info("endpoint request, uri:{}, params:{}, content type:{}, content length:{}", request.getRequestURI(),
                JsonUtils.toJson(request.getParameterMap()), request.getContentType(), request.getContentLength());
    }

    @Override
    public void postProcess(String uri, Context context) {
        try {
            MetricTags metricTags = MetricTags.of(context);
            var cost = System.currentTimeMillis() - context.getEndpointStartAt();

            MetricHelper.record(MetricName.request, cost, metricTags);
            var throwable = context.getResult().getThrowable();
            if (throwable.isPresent()) {
                metricTags.setExp(throwable.get().getClass().getName());
                MetricHelper.record(MetricName.exception, metricTags);
            }
        } catch (Throwable th) {
            log.error("postProcess error", th);
        } finally {
            MDC.clear();
        }
    }

    private void processResponse(HttpServletResponse response, ProxyResult result) {
        var headers = result.getHeaders();
        if (headers != null) {
            for (var header : headers) {
                response.addHeader(header.getName(), header.getValueString());
            }
        }

        var bytes = result.getBytes();
        try {
            bytes = bytes == null ? covertObject(result.getObject()) : bytes;
            if (bytes != null) {
                response.getOutputStream().write(bytes);
            }
        } catch (IOException e) {
            log.error("write response error", e);
            renderError(response, defaultFailResult);
            return;
        }

        if (response.getContentType() == null) {
            response.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
        }

        response.setStatus(HttpServletResponse.SC_OK);
    }

    private byte[] covertObject(Object object) throws JsonProcessingException {
        try {
            return objectMapper.writeValueAsBytes(object);
        } catch (JsonProcessingException e) {
            log.error("can't covert {}", object, e);
            throw e;
        }
    }

    private void renderError(HttpServletResponse resp, Result<Object> result) {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
        try {
            resp.getWriter().write(JsonUtils.toJson(result));
        } catch (IOException e) {
            log.error("write response failed");
        }
    }

    private void processError(Context context) {
        var resp = context.getResponse();
        var result = context.getResult();

        renderError(resp, Result.fail(result.getCode(), "系统异常"));
    }

    @ExceptionHandler(Throwable.class)
    public void processException(HttpServletResponse response, Throwable t) {
        log.error("caught exception", t);
        renderError(response, defaultFailResult);
    }
}
