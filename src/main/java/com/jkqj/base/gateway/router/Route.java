package com.jkqj.base.gateway.router;

import com.jkqj.base.gateway.handler.Handler;
import com.jkqj.base.gateway.handler.RootHandler;
import com.jkqj.base.gateway.middleware.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

import java.util.*;
import java.util.stream.Collectors;

@Data
@Slf4j
public class Route {
    private String path;
    private Set<HttpMethod> methods;
    private String description;
    private Boolean login;
    private Set<String> roles;
    private Set<String> permissions;
    private Set<String> passHeaders;
    private String traceHeader;
    private ProxyPass proxyPass;
    private List<Middleware> middlewares;
    private Handler root;
    private AntPathMatcher pathMatcher;
    private Integer timeout;

    public Route() {
        middlewares = new ArrayList<>();
        root = new RootHandler();
        roles = new HashSet<>();
        permissions = new HashSet<>();
        passHeaders = new HashSet<>();
        pathMatcher = new AntPathMatcher();
    }

    public void init(Set<String> sharingPaths, Set<String> guestPaths) {
        initMiddlewares(sharingPaths, guestPaths);
        initHandlerChain();
    }

    private void initHandlerChain() {
        var next = root;
        for (var middleware : middlewares) {
            next.next(middleware);
            next = next.next();
        }
        next.next(proxyPass.getUpstream());
    }

    public void initMiddlewares(Set<String> sharingPaths, Set<String> guestPaths) {
        //暂时不用trace middleware 在入口preProcess已处理
//        if (!Strings.isNullOrEmpty(traceHeader)) {
//            middlewares.add(new TraceMiddleware());
//        }
        var sharing = isPathMatch(this.path, sharingPaths);
        var guest = isPathMatch(this.path, guestPaths);

        if (sharing) {
            log.debug("got sharing path {}", path);
            middlewares.add(new ShareMiddleware());
        } else if (guest) {
            middlewares.add(new GuestMiddleware());
        } else {
            if (login) {
                middlewares.add(new AuthenticationMiddleware());
            } else {
                middlewares.add(new AuthOptionalMiddleware());
            }
        }

        if (CollectionUtils.isNotEmpty(passHeaders)) {
            middlewares.add(new HeaderMiddleware());
        }

        middlewares.add(new AuthorizationMiddleware());

        if (timeout != null) {
            log.debug("add timeout middleware for path {}", path);
            middlewares.add(new TimeoutMiddleware());
        }

        Collections.sort(middlewares);

        log.debug("initialized middlewares {}", middlewares);
    }

    private boolean isPathMatch(String path, Set<String> sharingPaths) {
        var comparator = pathMatcher.getPatternComparator(path);
        var list = new ArrayList<String>(sharingPaths);
        list.sort(comparator);
        return list.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));

    }

    public void setMethods(List<String> names) {
        this.methods = names.stream()
                .map(String::toUpperCase)
                .map(HttpMethod::resolve)
                .collect(Collectors.toSet());
    }

    public void addPassHeaders(Collection<String> headers) {
        if (headers == null) {
            return;
        }
        this.passHeaders.addAll(headers);
    }

    public void handle(Context context) {
        context.setRoute(this);
        root.handle(context);
    }

    public void addRoles(List<String> roles) {
        if (roles == null) {
            return;
        }
        this.roles.addAll(roles);
    }

    public void addPermissions(List<String> permissions) {
        if (permissions == null) {
            return;
        }
        this.permissions.addAll(permissions);
    }
}
