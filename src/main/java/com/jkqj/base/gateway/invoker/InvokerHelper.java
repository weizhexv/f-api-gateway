package com.jkqj.base.gateway.invoker;

import com.jkqj.base.gateway.upstream.DubboUpstream;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.rpc.service.GenericService;


public final class InvokerHelper {

    public static ReferenceConfig<GenericService> buildGenericService(DubboUpstream upstream) {
        var config = new ReferenceConfig<GenericService>();
        config.setInterface(upstream.getInterfaceName());
        config.setGroup(upstream.getGroup());
        config.setVersion(upstream.getVersion());
        config.setGeneric(CommonConstants.GENERIC_SERIALIZATION_DEFAULT);

        return config;
    }
}
