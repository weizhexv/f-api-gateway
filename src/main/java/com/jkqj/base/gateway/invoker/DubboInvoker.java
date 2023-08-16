package com.jkqj.base.gateway.invoker;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.ArrayType;
import com.fasterxml.jackson.databind.type.MapType;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.google.common.collect.Maps;
import com.jkqj.base.gateway.middleware.TraceMiddleware;
import com.jkqj.base.gateway.router.Context;
import com.jkqj.base.gateway.upstream.DubboUpstream;
import com.jkqj.common.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.service.GenericService;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.util.MimeTypeUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.jkqj.base.gateway.invoker.InvokerHelper.buildGenericService;
import static org.apache.commons.collections4.MapUtils.isNotEmpty;

@Slf4j
public class DubboInvoker implements Invoker {
    public static final String ARRAY_PREFIX = "[";
    private final ObjectMapper objectMapper;
    private final MapType mapType;
    private final ArrayType arrayType;
    private final Cache<String, ReferenceConfig<GenericService>> configCache;

    private static final Set<String> COMPOSITE_TYPES;
    private static final Set<String> PRIMITIVE_TYPES;

    static {
        COMPOSITE_TYPES = new HashSet<>();
        initCompositeTypes();

        PRIMITIVE_TYPES = new HashSet<>();
        initPrimitiveTypes();
    }

    private static void initCompositeTypes() {
        COMPOSITE_TYPES.add(Collection.class.getName());
        COMPOSITE_TYPES.add(List.class.getName());
        COMPOSITE_TYPES.add(Set.class.getName());
        COMPOSITE_TYPES.add(ArrayList.class.getName());
        COMPOSITE_TYPES.add(LinkedList.class.getName());
        COMPOSITE_TYPES.add(CopyOnWriteArrayList.class.getName());
        COMPOSITE_TYPES.add(HashSet.class.getName());
        COMPOSITE_TYPES.add(TreeSet.class.getName());
        COMPOSITE_TYPES.add(LinkedHashSet.class.getName());
        COMPOSITE_TYPES.add(ConcurrentSkipListSet.class.getName());
        COMPOSITE_TYPES.add(CopyOnWriteArraySet.class.getName());
    }

    private static void initPrimitiveTypes() {
        PRIMITIVE_TYPES.add(Boolean.TYPE.getName());
        PRIMITIVE_TYPES.add(Character.TYPE.getName());
        PRIMITIVE_TYPES.add(Byte.TYPE.getName());
        PRIMITIVE_TYPES.add(Short.TYPE.getName());
        PRIMITIVE_TYPES.add(Integer.TYPE.getName());
        PRIMITIVE_TYPES.add(Long.TYPE.getName());
        PRIMITIVE_TYPES.add(Float.TYPE.getName());
        PRIMITIVE_TYPES.add(Double.TYPE.getName());

        PRIMITIVE_TYPES.add(Boolean.class.getName());
        PRIMITIVE_TYPES.add(Character.class.getName());
        PRIMITIVE_TYPES.add(Byte.class.getName());
        PRIMITIVE_TYPES.add(Short.class.getName());
        PRIMITIVE_TYPES.add(Integer.class.getName());
        PRIMITIVE_TYPES.add(Long.class.getName());
        PRIMITIVE_TYPES.add(Float.class.getName());
        PRIMITIVE_TYPES.add(Double.class.getName());
    }

    public DubboInvoker(ObjectMapper objectMapper, Map<String, ReferenceConfig<GenericService>> genericServices) {
        this.objectMapper = objectMapper;
        this.mapType = objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class);
        this.arrayType = objectMapper.getTypeFactory().constructArrayType(Object.class);
        this.configCache = initConfigCache();
        loadConfigCache(genericServices);
    }

    private Cache<String, ReferenceConfig<GenericService>> initConfigCache() {
        return Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(1000)
                .expireAfterAccess(3, TimeUnit.DAYS)
                .evictionListener(this::destroyService)
                .recordStats()
                .build();
    }

    private void loadConfigCache(Map<String, ReferenceConfig<GenericService>> genericServices) {
        checkArgument(isNotEmpty(genericServices));
        checkState(configCache != null);

        configCache.putAll(genericServices);
        checkState(configCache.estimatedSize() > 0);
        log.info("load config cache size {}", configCache.estimatedSize());
    }

    private void destroyService(@Nullable String key, @Nullable ReferenceConfig<GenericService> config, RemovalCause cause) {
        log.debug("destroying {} with cause {}", key, cause.name());
        if (config != null) {
            config.destroy();
        }
    }

    @Override
    public void invoke(Context context) {
        var proxyPass = context.getRoute().getProxyPass();
        var upstream = (DubboUpstream) proxyPass.getUpstream();
        var genericService = buildGenericServer(proxyPass.getUri(), upstream);
        var parameterTypes = upstream.getParameterTypes();
        var args = buildArguments(parameterTypes, upstream.getParameterNames(), context.getRequest());
        populateInvocationContext(context);

        log.info("DubboInvoker.invoke method:{}, types:{}, params:{}", upstream.getMethodName(), parameterTypes, JsonUtils.toJson(args));
        long start = System.currentTimeMillis();
        var result = genericService.$invoke(upstream.getMethodName(), parameterTypes, args);
        log.info("DubboInvoker.invoke response:{}, cost:{}", Objects.nonNull(result), System.currentTimeMillis() - start);

        var headers = setContentType(Maps.newHashMap());
        context.success(headers, result);
    }

    private void populateInvocationContext(Context context) {
        var serviceContext = RpcContext.getServiceContext();
        var headers = context.getHeaders();
        log.info("add dubbo content: {}", JsonUtils.toJson(headers));
        for (var header : headers) {
            serviceContext.setAttachment(header.getName(), header.getValue());
        }

        serviceContext.setAttachment(TraceMiddleware.TRACE_ID, context.getTraceId());
    }

    private Object[] buildArguments(String[] parameterTypes, String[] parameterNames, HttpServletRequest request) {
        var arityType = ArityType.evaluate(parameterTypes, parameterNames);
        log.debug("building {} arguments", arityType);

        switch (arityType) {
            case NULLARY:
                return new Object[0];
            case UNARY_MAP:
                return buildUnaryMap(request);
            case UNARY_ARRAY:
                return buildUnaryArray(request);
            case UNARY_STRING:
                return buildUnaryString(request);
            case UNARY_PRIMITIVE:
                return buildUnaryPrimitive(parameterNames, request);
            case N_ARY:
                return buildNAry(parameterTypes, parameterNames, request);
            default:
                throw new UnsupportedOperationException();
        }
    }

    private Object[] buildUnaryPrimitive(String[] parameterNames, HttpServletRequest request) {
        var name = parameterNames[0];
        if (name == null) {
            if (request.getParameterNames().hasMoreElements()) {
                name = request.getParameterNames().nextElement();
            }
        }
        checkArgument(name != null);

        return new Object[]{request.getParameter(name)};
    }

    private Object[] buildUnaryString(HttpServletRequest request) {
        String content;
        try {
            content = request.getReader().lines().collect(Collectors.joining());
        } catch (IOException e) {
            log.error("can't read body", e);
            throw new RuntimeException(e);
        }
        if (StringUtils.isBlank(content)) {
            var names = request.getParameterNames();
            content = names.hasMoreElements() ? request.getParameter(names.nextElement()) : content;
        }
        return new Object[]{content};
    }

    private Object[] buildUnaryArray(HttpServletRequest request) {
        Optional<Object[]> object = buildBodyObject(request, arrayType);
        if (object.isEmpty()) {
            var names = request.getParameterNames();
            object = names.hasMoreElements()
                    ? Optional.ofNullable(request.getParameterValues(names.nextElement()))
                    : object;
        }
        var objects = object.orElse(new Object[0]);
        return new Object[]{objects};
    }

    private Object[] buildUnaryMap(HttpServletRequest request) {
        Optional<Map<String, Object>> bodyObject = buildBodyObject(request, mapType);
        var requestParameters = request.getParameterMap();
        var bodyParameters = bodyObject.orElse(new HashMap<>());

        if (requestParameters.isEmpty()) {
            return new Object[]{bodyParameters};
        }

        for (var entry : requestParameters.entrySet()) {
            var key = entry.getKey();
            if (!bodyParameters.containsKey(key)) {
                var value = entry.getValue();
                if (value != null && value.length == 1) {
                    bodyParameters.put(key, value[0]);
                } else {
                    bodyParameters.put(key, value);
                }
            }
        }

        return new Object[]{bodyParameters};
    }

    private Object[] buildNAry(String[] parameterTypes, String[] parameterNames, HttpServletRequest request) {
        Optional<Map<String, Object>> bodyParametersOpt = buildBodyObject(request, mapType);
        var bodyParameters = bodyParametersOpt.orElse(new HashMap<>());
        var requestParameters = request.getParameterMap();
        if (bodyParametersOpt.isEmpty()) {
            bodyParameters = new HashMap<>(requestParameters.size());
        }

        var args = new Object[parameterNames.length];
        for (int i = 0; i < parameterNames.length; i++) {
            var type = parameterTypes[i];
            var name = parameterNames[i];
            Object value;
            if (bodyParameters.containsKey(name)) {
                value = bodyParameters.get(name);
            } else {
                var values = requestParameters.get(name);
                if (values == null) {
                    value = null;
                } else {
                    if (isCompositeType(parameterTypes[i])) {
                        value = values;
                    } else {
                        value = values.length == 0 ? null : values[0];
                    }
                }
            }
            args[i] = wrapValues(type, value);
        }
        return args;
    }

    private Object wrapValues(String type, Object value) {
        if (!PrimitiveArrayTypes.contains(type) || value == null) {
            return value;
        }

        if (value instanceof String[]) {
            return Arrays.stream(((String[]) value)).map(it -> wrapValue(type, it)).toArray();
        }
        return wrapValue(type, (String) value);
    }

    private Object wrapValue(String type, String value) {
        return PrimitiveArrayTypes.of(type).parseValue(value);
    }

    private static boolean isCompositeType(String parameterType) {
        checkArgument(parameterType != null);
        return parameterType.startsWith(ARRAY_PREFIX) || COMPOSITE_TYPES.contains(parameterType);
    }

    private <T> Optional<T> buildBodyObject(HttpServletRequest request, JavaType javaType) {
        if (!StringUtils.contains(request.getContentType(), MimeTypeUtils.APPLICATION_JSON_VALUE)) {
            return Optional.empty();
        }

        T parameters;
        try {
            var hasBody = request.getInputStream().available() > 0;
            if (hasBody) {
                parameters = objectMapper.readValue(request.getInputStream(), javaType);
                log.debug("build body parameters {}", parameters);
                return Optional.of(parameters);
            } else {
                log.debug("no body in request");
                return Optional.empty();
            }
        } catch (IOException e) {
            log.error("can't handle input stream {}", request.getRequestURI(), e);
            throw new RuntimeException(e);
        }
    }

    private GenericService buildGenericServer(String uri, DubboUpstream upstream) {
        var service = configCache.get(uri, key -> buildGenericService(upstream)).get();

        log.debug("config cache stats {}", configCache.stats());
        return service;
    }

    private static boolean isArrayType(String typeName) {
        return typeName.startsWith(ARRAY_PREFIX);
    }

    private static boolean isStringType(String typeName) {
        return String.class.getName().equalsIgnoreCase(typeName);
    }

    private static boolean isPrimitiveType(String typeName) {
        return PRIMITIVE_TYPES.contains(typeName);
    }

    /**
     * all supported parameters likes:
     * <pre>
     * - path: /demo/unary-array
     *   methods:
     *     - get
     *     - post
     *   login: false
     *   proxyPass: dubbo://local:com.jkqj.base.gateway.invoker.demo.DemoService:unaryArray:([Lcom.jkqj.base.gateway.invoker.demo.PingRequest;):1.0.0
     * - path: /demo/nary-array
     *   methods:
     *     - get
     *     - post
     *   login: false
     *   proxyPass: dubbo://local:com.jkqj.base.gateway.invoker.demo.DemoService:unaryArray:([Lcom.jkqj.base.gateway.invoker.demo.PingRequest; requests):1.0.0
     * - path: /demo/unary-map
     *   methods:
     *     - get
     *     - post
     *   login: false
     *   proxyPass: dubbo://local:com.jkqj.base.gateway.invoker.demo.DemoService:unaryMap:(com.jkqj.base.gateway.invoker.demo.PingRequest):1.0.0
     * - path: /demo/nary-map
     *   methods:
     *     - get
     *     - post
     *   login: false
     *   proxyPass: dubbo://local:com.jkqj.base.gateway.invoker.demo.DemoService:unaryMap:(com.jkqj.base.gateway.invoker.demo.PingRequest request):1.0.0
     * - path: /demo/unary-string
     *   methods:
     *     - get
     *     - post
     *   login: false
     *   proxyPass: dubbo://local:com.jkqj.base.gateway.invoker.demo.DemoService:unaryString:(java.lang.String):1.0.0
     * - path: /demo/nary-string
     *   methods:
     *     - get
     *     - post
     *   login: false
     *   proxyPass: dubbo://local:com.jkqj.base.gateway.invoker.demo.DemoService:unaryString:(java.lang.String name):1.0.0
     * - path: /demo/unary-long
     *   methods:
     *     - get
     *     - post
     *   login: false
     *   proxyPass: dubbo://local:com.jkqj.base.gateway.invoker.demo.DemoService:unaryLong:(java.lang.Long):1.0.0
     * - path: /demo/nary-long
     *   methods:
     *     - get
     *     - post
     *   login: false
     *   proxyPass: dubbo://local:com.jkqj.base.gateway.invoker.demo.DemoService:unaryLong:(java.lang.Long num):1.0.0
     * - path: /demo/unary-int
     *   methods:
     *     - get
     *     - post
     *   login: false
     *   proxyPass: dubbo://local:com.jkqj.base.gateway.invoker.demo.DemoService:unaryInt:(int):1.0.0
     * - path: /demo/nary-int
     *   methods:
     *     - get
     *     - post
     *   login: false
     *   proxyPass: dubbo://local:com.jkqj.base.gateway.invoker.demo.DemoService:unaryInt:(int num):1.0.0
     * - path: /demo/nary-int-array
     *   methods:
     *     - get
     *     - post
     *   login: false
     *   proxyPass: dubbo://local:com.jkqj.base.gateway.invoker.demo.DemoService:unaryIntArray:([I numbers):1.0.0
     * - path: /demo/nary
     *   methods:
     *     - get
     *     - post
     *   login: false
     *   proxyPass: dubbo://local:com.jkqj.base.gateway.invoker.demo.DemoService:nAry:(java.lang.String name, int age, boolean isMale, [Ljava.lang.String; hobbies):1.0.0
     * - path: /demo/nary-array
     *   methods:
     *     - get
     *     - post
     *   login: false
     *   proxyPass: dubbo://local:com.jkqj.base.gateway.invoker.demo.DemoService:nAryArray:([I numbers, [Ljava.lang.String; hobbies):1.0.0
     *  </pre>
     */
    private enum ArityType {
        NULLARY,
        //UNARY_xxx only use parameter type, don't set parameter name
        UNARY_ARRAY,
        UNARY_MAP,
        UNARY_STRING,
        UNARY_PRIMITIVE,
        //Use parameter names to retrieve args from query and body
        N_ARY;

        public static ArityType evaluate(String[] types, String[] names) {
            log.debug("evaluating arity type, {}, {}", types, names);

            if (ArrayUtils.isEmpty(types)) {
                return NULLARY;
            }

            if (types.length == 1) {
                var firstType = types[0];
                checkState(firstType != null);

                if (isPrimitiveType(firstType)) {
                    return UNARY_PRIMITIVE;
                }

                if (names[0] == null) {
                    if (isArrayType(firstType)) {
                        return UNARY_ARRAY;
                    } else if (isStringType(firstType)) {
                        return UNARY_STRING;
                    } else {
                        return UNARY_MAP;
                    }
                }
            }

            if (ArrayUtils.isNotEmpty(names) && names[0] != null) {
                return N_ARY;
            } else {
                return UNARY_MAP;
            }
        }
    }

    private enum PrimitiveArrayTypes {
        BOOLEAN("[Z", Boolean::parseBoolean),
        CHAR("[C", it -> it.charAt(0)),
        BYTE("[B", Byte::parseByte),
        SHORT("[S", Short::parseShort),
        INT("[I", Integer::parseInt),
        LONG("[J", Long::parseLong),
        FLOAT("[F", Float::parseFloat),
        DOUBLE("D", Double::parseDouble);

        private final String symbol;
        private final Function<String, Object> parser;

        PrimitiveArrayTypes(String symbol, Function<String, Object> parser) {
            this.symbol = symbol;
            this.parser = parser;
        }

        public Object parseValue(String value) {
            return this.parser.apply(value);
        }

        public static PrimitiveArrayTypes of(String symbol) {
            return Arrays.stream(values()).filter(it -> it.symbol.equals(symbol)).findFirst().orElseThrow();
        }

        public static boolean contains(String symbol) {
            return Arrays.stream(values()).anyMatch(it -> it.symbol.equals(symbol));
        }
    }

}
