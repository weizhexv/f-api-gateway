package com.jkqj.base.gateway.router;


import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpMethod;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

public class Context {
    @Getter
    private String uri;
    @Getter
    private HttpServletRequest request;
    @Getter
    private HttpServletResponse response;
    @Getter
    @Setter
    private Route route;
    @Getter
    @Setter
    private List<Header> headers;
    @Getter
    @Setter
    private String traceId;
    @Getter
    @Setter
    private boolean trace;
    @Getter
    @Setter
    private ProxyResult result;
    @Getter
    @Setter
    private Long uid;
    @Getter
    @Setter
    private Long opId;
    @Getter
    @Setter
    private Long endpointStartAt;
    @Getter
    @Setter
    private Long upstreamStartAt;
    private final Map<String, Object> attributes;

    public Context(HttpServletRequest request, HttpServletResponse response) {
        this.uri = request.getRequestURI();
        this.request = request;
        this.response = response;
        this.headers = new ArrayList<>();
        this.attributes = new HashMap<>();
    }

    public HttpMethod getMethod() {
        return HttpMethod.resolve(request.getMethod());
    }

    public void addHeader(Header header) {
        this.headers.add(header);
    }

    public void addHeader(String name, Enumeration<String> values) {
        if (values == null || !values.hasMoreElements()) {
            return;
        }

        while (values.hasMoreElements()) {
            String value = values.nextElement();
            this.headers.add(new Header(name, value));
        }
    }

    public String getUri() {
        return uri;
    }

    public void success(byte[] bytes) {
        result = new ProxyResult(bytes);
    }

    public void success(Object object) {
        result = new ProxyResult(object);
    }

    public void success(List<Header> headers, byte[] bytes) {
        result = new ProxyResult(headers, bytes);
    }

    public void success(List<Header> headers, Object object) {
        result = new ProxyResult(headers, object);
    }

    public void success(Map<String, List<String>> headerMap, byte[] bytes) {
        ArrayList<Header> headers = flatHeaders(headerMap);
        result = new ProxyResult(headers, bytes);
    }

    public void success(Map<String, List<String>> headerMap, Object object) {
        ArrayList<Header> headers = flatHeaders(headerMap);
        result = new ProxyResult(headers, object);
    }

    private static ArrayList<Header> flatHeaders(Map<String, List<String>> headerMap) {
        var headers = new ArrayList<Header>();
        for (var entry : headerMap.entrySet()) {
            for (var value : entry.getValue()) {
                headers.add(new Header(entry.getKey(), value));
            }
        }
        return headers;
    }

    public void fail(int code) {
        result = new ProxyResult(code);
    }

    public void fail(int code, String message) {
        result = new ProxyResult(code, message);
    }

    public void fail(int code, String message, Throwable throwable) {
        result = new ProxyResult(code, message, throwable);
    }

    public void success() {
        result = new ProxyResult(0);
    }

    public Optional<Header> getHeader(String name) {
        return headers.stream().filter(header -> header.getName().equalsIgnoreCase(name)).findFirst();
    }

    public <V> void addAttribute(String name, V value) {
        attributes.put(name, value);
    }

    @SuppressWarnings("unchecked")
    public <V> V getAttribute(String name) {
        return (V) attributes.get(name);
    }
}
