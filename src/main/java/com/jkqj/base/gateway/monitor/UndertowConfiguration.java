package com.jkqj.base.gateway.monitor;

import org.springframework.boot.web.embedded.undertow.UndertowDeploymentInfoCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author xuweizhe@reta-inc.com
 * @date 2022/2/14
 * @description
 */
@Configuration
public class UndertowConfiguration {
    @Bean
    UndertowDeploymentInfoCustomizer undertowDeploymentInfoCustomizer(
            UndertowMetricsHandlerWrapper undertowMetricsHandlerWrapper) {

        return deploymentInfo ->
                deploymentInfo.addOuterHandlerChainWrapper(undertowMetricsHandlerWrapper);
    }
}
