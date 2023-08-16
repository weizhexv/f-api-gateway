package com.jkqj.base.gateway.router;

import lombok.Data;

import java.util.List;
import java.util.Optional;

@Data
public class ProxyResult {
    private int code;
    private String message;
    private List<Header> headers;
    private Object object;
    private byte[] bytes;
    private Throwable throwable;

    public ProxyResult(int code) {
        this.code = code;
    }

    public ProxyResult(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public ProxyResult(int code, String message, Throwable throwable) {
        this.code = code;
        this.message = message;
        this.throwable = throwable;
    }

    public ProxyResult(byte[] bytes) {
        this.code = 0;
        this.bytes = bytes;
    }

    public ProxyResult(List<Header> headers, byte[] bytes) {
        this.code = 0;
        this.headers = headers;
        this.bytes = bytes;
    }

    public ProxyResult(Object object) {
        this.code = 0;
        this.object = object;
    }

    public ProxyResult(List<Header> headers, Object object) {
        this.code = 0;
        this.headers = headers;
        this.object = object;
    }

    public boolean isSuccess() {
        return code == 0;
    }

    public boolean isFailed() {
        return !isSuccess();
    }

    public Optional<Throwable> getThrowable() {
        return isFailed() ? Optional.ofNullable(throwable) : Optional.empty();
    }
}
