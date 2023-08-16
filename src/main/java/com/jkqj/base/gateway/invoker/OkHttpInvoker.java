package com.jkqj.base.gateway.invoker;

import com.jkqj.base.gateway.router.Context;
import com.jkqj.base.gateway.router.Header;
import com.jkqj.base.gateway.upstream.HttpUpstream;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpMethod;
import org.springframework.util.MimeTypeUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.replaceIgnoreCase;

@Slf4j
public class OkHttpInvoker implements Invoker {
    private static final String SCHEMA = "http";
    public static final String VAR_REQUEST_URI = "{request_uri}";
    public static final String HEADER_CONNECTION = "Connection";
    private final OkHttpClient client;
    private final static Set<String> NO_BODY_METHODS;

    static {
        NO_BODY_METHODS = new HashSet<>();
        NO_BODY_METHODS.add(HttpMethod.GET.name());
        NO_BODY_METHODS.add(HttpMethod.HEAD.name());
        NO_BODY_METHODS.add(HttpMethod.DELETE.name());
        NO_BODY_METHODS.add(HttpMethod.OPTIONS.name());
        NO_BODY_METHODS.add(HttpMethod.TRACE.name());
    }

    public OkHttpInvoker(OkHttpClient client) {
        this.client = client;
    }

    @Override
    public void invoke(Context context) {
        var pathTemplate = context.getRoute().getProxyPass().getUri();
        var upstream = (HttpUpstream) context.getRoute().getProxyPass().getUpstream();
        var request = prepareRequest(upstream.getHost(), upstream.getPort(), pathTemplate, context.getRequest(), context.getHeaders());
        invokeRequest(request, context);
    }

    private void invokeRequest(Request request, Context context) {
        try (var response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                var headers = response.headers().toMultimap();
                headers = setContentType(headers);
                byte[] bytes = new byte[0];
                if (response.body() != null) {
                    bytes = response.body().bytes();
                }
                context.success(headers, bytes);
            } else {
                context.fail(1);
            }
        } catch (IOException e) {
            log.error("execute request error", e);
            context.fail(1);
        }
    }

    private void invokeRequestAsync(Request request, Context context) {
        var callback = new HttpCallback(context);
        client.newCall(request).enqueue(callback);
        callback.await();
    }

    private Request prepareRequest(String host, Integer port, String pathTemplate, HttpServletRequest servletRequest, List<Header> headerList) {
        var url = createUrl(host, port, pathTemplate, servletRequest);
        var headers = createHeaders(headerList);
        var body = createBody(servletRequest);
        var request = createRequest(servletRequest.getMethod(), url, headers, body);

        log.debug("preparing request {}", request);

        return request;
    }

    private Request createRequest(String method, HttpUrl url, Headers headers, RequestBody body) {
        return new Request.Builder()
                .url(url)
                .method(method, body)
                .headers(headers)
                .build();
    }

    private RequestBody createBody(HttpServletRequest servletRequest) {
        if (NO_BODY_METHODS.contains(servletRequest.getMethod())) {
            log.info("no body methods, method:{}", servletRequest.getMethod());
            return null;
        }

        byte[] content;
        try {
            content = servletRequest.getInputStream().readAllBytes();
        } catch (IOException e) {
            log.error("read request stream error", e);
            throw new RuntimeException(e);
        }

        var mediaType = servletRequest.getContentType() != null
                ? MediaType.parse(servletRequest.getContentType())
                : MediaType.parse(MimeTypeUtils.APPLICATION_JSON_VALUE);

        if (servletRequest.getContentLength() < 4096 && nonNull(content)) {
            log.info("http media type:{}, content: {}", mediaType, new String(content, StandardCharsets.UTF_8));
        }

        return RequestBody.create(mediaType, content);
    }

    private Headers createHeaders(List<Header> headerList) {
        var builder = new Headers.Builder();
        if (headerList != null) {
            for (var header : headerList) {
                builder.add(header.getName(), header.getValueString());
            }
        }
        builder.add(HEADER_CONNECTION, "close");

        return builder.build();
    }

    private HttpUrl createUrl(String host, Integer port, String pathTemplate, HttpServletRequest servletRequest) {
        var path = replaceIgnoreCase(pathTemplate, VAR_REQUEST_URI, servletRequest.getRequestURI());
        var url = new HttpUrl.Builder()
                .scheme(SCHEMA)
                .host(host)
                .port(port)
                .encodedPath(path)
                .encodedQuery(servletRequest.getQueryString())
                .build();

        log.debug("created http url {}", url);
        return url;
    }

    private static class HttpCallback implements Callback {
        private final CompletableFuture<Response> future;
        private final Context context;

        public HttpCallback(Context context) {
            this.future = new CompletableFuture<>();
            this.context = context;
            future.thenAccept(this::processResponse);
        }

        private void processResponse(Response response) {
            try (response) {
                if (response.isSuccessful()) {
                    var headers = response.headers().toMultimap();
                    byte[] bytes = new byte[0];
                    if (response.body() != null) {
                        bytes = response.body().bytes();
                    }
                    context.success(headers, bytes);
                } else {
                    context.fail(1);
                }
            } catch (IOException e) {
                log.debug("process response error", e);
                context.fail(1);
            }
        }

        public void await() {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                log.error("await error", e);
            }
        }

        @Override
        public void onFailure(Call call, IOException e) {
            future.completeExceptionally(e);
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            future.complete(response);
        }
    }
}
