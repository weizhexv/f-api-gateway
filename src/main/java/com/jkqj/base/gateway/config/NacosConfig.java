package com.jkqj.base.gateway.config;

import com.alibaba.nacos.api.annotation.NacosInjected;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.alibaba.nacos.api.exception.NacosException;
import com.jkqj.base.gateway.router.RouterHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.PreDestroy;
import java.util.*;

import static com.google.common.base.Preconditions.checkState;

@Component
@Slf4j
public class NacosConfig {
    private static final long TIMEOUT_10S = 10_000L;

    @Value("${jkqj.gateway.nacos.group}")
    private String group;

    @Value("${jkqj.gateway.nacos.globalId}")
    private String globalId;

    @Value("${jkqj.gateway.nacos.routeTriggerId}")
    private String routeTriggerId;

    @Value("${jkqj.gateway.nacos.routeIdSuffix}")
    private String routeIdSuffix;

    @NacosInjected
    private ConfigService configService;

    private RouteTrigger lastRouteTrigger;

    @PreDestroy
    public void stopGracefully() {
        if (configService != null) {
            try {
                configService.shutDown();
            } catch (NacosException e) {
                log.error("nacos shutdown ex", e);
            }
        }
    }

    private List<RuleProperties> loadRouteRule(String dataId) {
        try {
            var yaml = configService.getConfig(dataId, group, TIMEOUT_10S);
            return new Yaml().loadAs(yaml, RouterProperties.class).getRules();
        } catch (Throwable t) {
            log.error("can't load {}, group {}", dataId, group, t);
            return null;
        }
    }

    public void setLastRouteTrigger() {
        this.lastRouteTrigger = loadRouteTrigger().orElse(null);
    }

    private Optional<RouteTrigger> loadRouteTrigger() {
        try {
            String text = configService.getConfig(routeTriggerId, group, TIMEOUT_10S);
            return RouteTrigger.from(text);
        } catch (Throwable t) {
            log.error("route index {} group {} can't load", routeTriggerId, group, t);
            return Optional.empty();
        }
    }

    public RouterConfig loadConfig() throws NacosException {
        String yaml;
        try {
            yaml = configService.getConfig(globalId, group, TIMEOUT_10S);
        } catch (Throwable t) {
            log.error("nacos get config error dataId {}, group {}", globalId, group, t);
            throw t;
        }

        log.info("nacos get config by dataId {}, group {}, result {}", globalId, group, yaml);
        return load(yaml);
    }

    public void registerListener(RouterHelper routerHelper) throws NacosException {
        configService.addListener(globalId, group, new ConfigListener(routerHelper));
        configService.addListener(routeTriggerId, group, new TriggerListener(routerHelper));
    }

    private RouterConfig load(String yaml) {
        checkState(StringUtils.isNotBlank(yaml));

        var routerProperties = new Yaml().loadAs(yaml, RouterProperties.class);
        checkState(Objects.nonNull(routerProperties));

        var ruleProperties = loadRulesFromDataIds(routerProperties.getRuleDataIds());
        routerProperties.getRules().addAll(ruleProperties);
        routerProperties.validate();

        return new RouterConfig(routerProperties);
    }

    private List<RuleProperties> loadRulesFromDataIds(Collection<String> ruleDataIds) {
        if (CollectionUtils.isEmpty(ruleDataIds)) {
            return Collections.emptyList();
        }

        log.debug("loading rule from data ids {}", ruleDataIds);

        return ruleDataIds.stream()
                .map(it -> it + routeIdSuffix)
                .map(this::loadRouteRule)
                .reduce((acc, list) -> {
                    if (list != null) {
                        acc.addAll(list);
                    }
                    return acc;
                })
                .orElse(new ArrayList<>());
    }

    private class ConfigListener extends AbstractListener {
        private final RouterHelper routerHelper;

        public ConfigListener(RouterHelper routerHelper) {
            this.routerHelper = routerHelper;
        }

        @Override
        public void receiveConfigInfo(String content) {
            log.info("config listener receive {}", content);
            try {
                var routerConfig = load(content);
                routerHelper.refresh(routerConfig);
            } catch (Throwable th) {
                log.error("config refresh error", th);
            }
        }
    }

    private class TriggerListener extends AbstractListener {
        private final RouterHelper routerHelper;

        public TriggerListener(RouterHelper routerHelper) {
            this.routerHelper = routerHelper;
        }

        @Override
        public void receiveConfigInfo(String content) {
            log.info("received route index update {}", content);

            var routeTrigger = RouteTrigger.from(content);
            if (routeTrigger.isEmpty()) {
                log.info("empty route index");
                return;
            }

            if (routeTrigger.get().after(lastRouteTrigger)) {
                try {
                    var routerConfig = loadConfig();
                    routerHelper.refresh(routerConfig);
                } catch (NacosException e) {
                    log.error("reload by trigger failed {}", content, e);
                }
            }

            lastRouteTrigger = routeTrigger.get();
        }
    }
}