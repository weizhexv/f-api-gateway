package com.jkqj.base.gateway.router;

import com.jkqj.base.gateway.config.ProxyPassProperties;
import com.jkqj.base.gateway.config.RouterConfig;
import com.jkqj.base.gateway.config.RuleProperties;
import com.jkqj.base.gateway.config.UpstreamProperties;
import com.jkqj.base.gateway.upstream.DubboUpstream;
import com.jkqj.base.gateway.upstream.HttpUpstream;
import com.jkqj.base.gateway.upstream.Upstream;
import com.jkqj.base.gateway.upstream.Upstreams;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.AntPathMatcher;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
@Data
public class Router {
    public static final String PARAMETER_OPEN = "(";
    public static final String PARAMETER_CLOSE = ")";
    public static final String PARAMETER_SEP = ",";
    private static final String PARAMETER_TYPE_SEP = " ";

    @Getter
    private volatile RouterConfig config;

    private Set<String> passHeaders;

    private Set<String> sharingPaths;

    private Set<String> guestPaths;

    private ConcurrentMap<String, Upstream> upstreams;

    private ConcurrentMap<String, Route> routes;

    private AntPathMatcher matcher;

    public void init(RouterConfig config) {
        log.debug("initializing with config \n{}", config);
        checkArgument(Objects.nonNull(config));
        this.config = config;
        routes = new ConcurrentHashMap<>();
        upstreams = new ConcurrentHashMap<>();
        matcher = new AntPathMatcher();
        buildRouter();

        log.debug("initialized routes \n{}", routes);
        log.debug("initialized upstreams \n{}", upstreams);
        log.debug("initialized passHeaders \n{}", passHeaders);
        log.debug("initialized sharingPaths \n{}", sharingPaths);
    }

    public void refresh(RouterConfig config) {
        log.debug("refreshing with properties \n{}", config);
        checkArgument(Objects.nonNull(config));
        this.config = config;
        resetRouter();
        buildRouter();
        log.debug("refreshed routes \n{}", routes);
        log.debug("refreshed upstreams \n{}", upstreams);
        log.debug("refreshed passHeaders \n{}", passHeaders);
        log.debug("initialized sharingPaths \n{}", sharingPaths);
    }

    private void resetRouter() {
        passHeaders = null;
        sharingPaths = null;
        routes.clear();
        upstreams.clear();
    }

    private void buildRouter() {
        config.validate();

        var properties = config.getProperties();
        this.passHeaders = properties.getPassHeaders();
        this.sharingPaths = Optional.ofNullable(properties.getSharingPaths()).orElse(Collections.emptySet());
        this.guestPaths = Optional.ofNullable(properties.getGuestPaths()).orElse(Collections.emptySet());
        initUpstreams(properties.getUpstreams());

        for (var rule : properties.getRules()) {
            if (routes.containsKey(rule.getPath())) {
                log.warn("route duplicated {}", rule);
            }
            routes.put(rule.getPath(), buildRoute(rule));
        }
    }

    private void initUpstreams(List<UpstreamProperties> propertiesList) {
        for (var properties : propertiesList) {
            var key = getUpstreamKey(properties.getType(), properties.getId());
            upstreams.put(key, buildUpstream(properties));
        }
    }

    private String getUpstreamKey(String type, String id) {
        return Upstreams.valueOf(type.toUpperCase()).prefix() + id;
    }

    private Route buildRoute(RuleProperties properties) {
        var route = new Route();
        route.setPath(properties.getPath());
        route.setMethods(properties.getMethods());
        route.setDescription(properties.getDescription());
        route.setLogin(properties.getLogin());
        route.addRoles(properties.getRoles());
        route.addPermissions(properties.getPermissions());
        route.addPassHeaders(properties.getPassHeaders());
        route.addPassHeaders(this.passHeaders);
        route.setTraceHeader(properties.getTraceHeader());
        route.setProxyPass(buildProxyPass(properties.getProxyPass()));
        route.setTimeout(properties.getTimeout());

        route.init(this.sharingPaths, this.guestPaths);
        return route;
    }

    private ProxyPass buildProxyPass(String proxyPassUri) {
        var protocolParts = proxyPassUri.split(ProxyPassProperties.PROTOCOL_SEP);
        checkArgument(protocolParts.length == 2);
        var protocol = protocolParts[0];

        var pathParts = StringUtils.splitPreserveAllTokens(protocolParts[1], ProxyPassProperties.PATH_SEP);
        checkArgument(pathParts.length >= 2);

        var proxyPass = new ProxyPass();
        var type = Upstreams.valueOf(protocol.toUpperCase());
        if (type == Upstreams.HTTP) {
            checkArgument(pathParts.length == 2);
            var id = pathParts[0];
            var upstreamKey = getUpstreamKey(protocol, id);
            var upstream = this.upstreams.get(upstreamKey);
            checkArgument(upstream != null);
            proxyPass.setUpstream(upstream);
            proxyPass.setUri(pathParts[1]);
        } else {
            checkArgument(pathParts.length == 5);
            var upstream = new DubboUpstream();
            upstream.setGroup(pathParts[0]);
            upstream.setInterfaceName(pathParts[1]);
            upstream.setMethodName(pathParts[2]);
            upstream.setParameters(buildParameters(pathParts[3]));
            upstream.setVersion(pathParts[4]);
            proxyPass.setUpstream(upstream);
            proxyPass.setUri(protocolParts[1]);
        }

        return proxyPass;
    }

    private List<DubboUpstream.Parameter> buildParameters(String string) {
        string = StringUtils.substringBetween(string, PARAMETER_OPEN, PARAMETER_CLOSE);
        if (StringUtils.isBlank(string)) {
            return null;
        }

        var rawParameters = string.split(PARAMETER_SEP);
        var parameters = new ArrayList<DubboUpstream.Parameter>();
        for (var parameter : rawParameters) {
            checkArgument(StringUtils.isNotBlank(parameter));
            var typeAndName = StringUtils.splitByWholeSeparator(parameter, PARAMETER_TYPE_SEP);
            checkArgument(typeAndName != null && typeAndName.length > 0);
            parameters.add(new DubboUpstream.Parameter(typeAndName));
        }
        return parameters;
    }

    private Upstream buildUpstream(UpstreamProperties properties) {
        var type = Upstreams.valueOf(properties.getType().toUpperCase());
        checkArgument(Upstreams.HTTP.equals(type));
        var upstream = new HttpUpstream();
        upstream.setId(properties.getId());
        upstream.setHost(properties.getHost());
        upstream.setPort(properties.getPort());

        return upstream;
    }

    //TODO: add guava cache for uri -> route
    private Optional<Route> match(String uri, Context context) {
        log.debug("matching uri {}", uri);

        var keys = new ArrayList<>(routes.keySet());
        var comparator = matcher.getPatternComparator(uri);
        keys.sort(comparator);
        var key = keys.stream()
                .filter(pattern -> matcher.match(pattern, uri))
                .findFirst();

        if (key.isEmpty()) {
            log.debug("mismatch uri {}", uri);
            return Optional.empty();
        }

        var route = routes.get(key.get());
        var method = context.getMethod();
        if (!route.getMethods().contains(method)) {
            log.warn("mismatch HttpMethod, uri {},  route method {}, target method {}",
                    uri, route.getMethods(), method);
            return Optional.empty();
        }

        log.debug("matched route {}", route);

        return Optional.of(route);
    }

    public void handle(String uri, Context context) {
        var route = match(uri, context);
        if (route.isPresent()) {
            route.get().handle(context);
        } else {
            context.fail(1, "无效的请求");
        }
    }
}
