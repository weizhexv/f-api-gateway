package com.jkqj.base.gateway.invoker.dubbo;

import com.jkqj.common.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;
import org.slf4j.MDC;

import java.util.UUID;

import static org.apache.dubbo.common.constants.CommonConstants.CONSUMER;

@Slf4j
@Activate(group = CONSUMER)
public class TraceConsumerFilter implements Filter {
    private static final String TRACE_ID = "trace-id";

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        RpcServiceContext context = RpcContext.getServiceContext();

        String traceId = (String) context.getObjectAttachment(TRACE_ID);

        if (StringUtils.isNotBlank(traceId)) {
            return invoker.invoke(invocation);
        }

        if (StringUtils.isBlank(traceId)) {
            traceId = MDC.get(TRACE_ID);
        }

        if (StringUtils.isBlank(traceId)) {
            traceId = UUID.randomUUID().toString();
        }

        context.setObjectAttachment(TRACE_ID, traceId);
        log.info("context attachments {}", JsonUtils.toJson(context.getObjectAttachments()));

        return invoker.invoke(invocation);
    }
}
