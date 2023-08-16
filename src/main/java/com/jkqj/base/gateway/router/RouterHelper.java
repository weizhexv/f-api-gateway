package com.jkqj.base.gateway.router;

import com.jkqj.base.gateway.config.NacosConfig;
import com.jkqj.base.gateway.config.RouterConfig;
import com.jkqj.common.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import static com.google.common.base.Preconditions.checkState;

@Slf4j
@Component
public class RouterHelper {
    private volatile Router currentRouter;

    @Autowired
    private NacosConfig nacosConfig;

    @PostConstruct
    public void init() throws Exception {
        var routerConfig = nacosConfig.loadConfig();
        checkState(routerConfig != null, "router config can't empty");

        currentRouter = buildRouter(routerConfig);

        nacosConfig.setLastRouteTrigger();
        nacosConfig.registerListener(this);
    }

    public synchronized void refresh(RouterConfig routerConfig) {
        Router router = null;
        try {
            router = buildRouter(routerConfig);
        } catch (Throwable t) {
            log.error("can't refresh router", t);
        }
        currentRouter = router == null ? currentRouter : router;
    }

    private Router buildRouter(RouterConfig routerConfig) {
        log.info("init router routerConfig: {}", JsonUtils.toJson(routerConfig));

        var router = new Router();
        router.init(routerConfig);

        return router;
    }

    public Router get() {
        return currentRouter;
    }
}
