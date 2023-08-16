package com.jkqj.base.gateway.invoker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.jkqj.base.gateway.router.Route;
import com.jkqj.base.gateway.router.RouterHelper;
import com.jkqj.base.gateway.upstream.DubboUpstream;
import com.jkqj.base.gateway.upstream.Upstreams;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.rpc.service.GenericService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkState;
import static org.apache.commons.collections4.MapUtils.isNotEmpty;

@Component
@Slf4j
public class InvokerFactory {
    private static InvokerFactory INSTANCE;
    private OkHttpClient httpClient;
    private OkHttpInvoker httpInvoker;
    private DubboInvoker dubboInvoker;

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RouterHelper routerHelper;


    public static Invoker getInvoker(Upstreams type) {
        switch (type) {
            case HTTP:
                return INSTANCE.httpInvoker;
            case DUBBO:
                return INSTANCE.dubboInvoker;
            default:
                throw new UnsupportedOperationException();
        }
    }

    private void initHttpClient() {
        var pool = new ConnectionPool(10, 5, TimeUnit.MINUTES);
        var dispatcher = createOkHttpDispatcher();
        var loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        httpClient = new OkHttpClient.Builder()
                .connectionPool(pool)
                .dispatcher(dispatcher)
                .callTimeout(Duration.ofMillis(20000L))
                .connectTimeout(Duration.ofMillis(10000L))
                .readTimeout(Duration.ofMillis(15000L))
                .writeTimeout(Duration.ofMillis(15000L))
                .addInterceptor(loggingInterceptor)
                .followRedirects(true)
                .retryOnConnectionFailure(false)
                .build();
    }

    private Dispatcher createOkHttpDispatcher() {
        var parallelism = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1);
        var exHandler = new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                log.error("thread {} error", t.getName(), e);
            }
        };

        var factory = new ForkJoinPool.ForkJoinWorkerThreadFactory() {
            @Override
            public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
                final var thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                thread.setName("okhttp-pool-" + thread.getPoolIndex());
                thread.setUncaughtExceptionHandler(exHandler);
                return thread;
            }
        };

        var corePoolSize = Long.valueOf(Math.round(parallelism * 1.5)).intValue();
        var maxPoolSize = parallelism * 2;
        var minRunnable = 1;
        var forkJoinPool = new ForkJoinPool(
                parallelism, factory, exHandler, true,
                corePoolSize, maxPoolSize, minRunnable,
                null, 5, TimeUnit.MINUTES
        );

        return new Dispatcher(forkJoinPool);
    }

    @PostConstruct
    public void postConstruct() {
        log.debug("initializing invoker factory...");

        initHttpClient();
        initInvoker();

        INSTANCE = this;
    }

    private Map<String, ReferenceConfig<GenericService>> initGenericServices() {
        var router = routerHelper.get();
        checkState(router != null);

        var routes = router.getRoutes();
        checkState(isNotEmpty(routes));

        Map<String, ReferenceConfig<GenericService>> cacheMap = Maps.newHashMap();
        for (Route route : routes.values()) {
            var proxyPass = route.getProxyPass();
            checkState(proxyPass != null);

            var uri = proxyPass.getUri();
            var upstream = proxyPass.getUpstream();
            checkState(uri != null);
            checkState(upstream != null);

            if (Upstreams.DUBBO.equals(upstream.getType())) {
                var genericService
                        = InvokerHelper.buildGenericService((DubboUpstream) upstream);

                cacheMap.put(uri, genericService);
            }
        }
        return cacheMap;
    }

    private void initInvoker() {
        httpInvoker = new OkHttpInvoker(httpClient);
        dubboInvoker = new DubboInvoker(objectMapper, initGenericServices());
    }

    @PreDestroy
    public void preDestroy() {
        if (httpClient != null) {
            try {
                httpClient.connectionPool().evictAll();
                var dispatcher = httpClient.dispatcher();
                dispatcher.cancelAll();
                var timeout = dispatcher.executorService().awaitTermination(1000, TimeUnit.MILLISECONDS);
                log.info("shutdown timeout {}", timeout);
            } catch (Throwable t) {
                log.error("destroying okhttp client", t);
            }
        }
    }
}
