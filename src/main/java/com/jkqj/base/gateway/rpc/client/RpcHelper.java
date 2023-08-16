package com.jkqj.base.gateway.rpc.client;

import com.jkqj.uc.client.AuthRpcService;
import com.jkqj.uc.client.BizUserRpcService;
import com.jkqj.uc.client.OrderRpcService;
import com.jkqj.uc.client.UserRpcService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
public class RpcHelper {
    private static RpcHelper INSTANCE;

    @Autowired
    private BizUserRpcService bizUserRpcService;

    @Autowired
    private UserRpcService userRpcService;

    @Autowired
    private AuthRpcService authRpcService;

    @Autowired
    private OrderRpcService orderRpcService;

    @PostConstruct
    public void postConstruct() {
        INSTANCE = this;
    }

    @Bean
    @DubboReference(version = "1.0.0", check = false)
    public ReferenceBean<UserRpcService> userRpcService() {
        return new ReferenceBean<>();
    }

    @Bean
    @DubboReference(version = "1.0.0", check = false)
    public ReferenceBean<AuthRpcService> authRpcService() {
        return new ReferenceBean<>();
    }

    @Bean
    @DubboReference(version = "1.0.0", check = false)
    public ReferenceBean<BizUserRpcService> bizUserRpcService() {
        return new ReferenceBean<>();
    }

    @Bean
    @DubboReference(version = "1.0.0", check = false)
    public ReferenceBean<OrderRpcService> orderRpcService() {
        return new ReferenceBean<>();
    }

    public static UserRpcService getUserRpcService() {
        return INSTANCE.userRpcService;
    }

    public static AuthRpcService getAuthRpcService() {
        return INSTANCE.authRpcService;
    }

    public static BizUserRpcService getBizUserRpcService() {
        return INSTANCE.bizUserRpcService;
    }

    public static OrderRpcService getOrderRpcService() {
        return INSTANCE.orderRpcService;
    }

}
