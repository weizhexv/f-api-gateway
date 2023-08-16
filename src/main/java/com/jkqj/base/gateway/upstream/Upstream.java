package com.jkqj.base.gateway.upstream;

import com.jkqj.base.gateway.handler.BaseHandler;
import com.jkqj.base.gateway.handler.Handler;
import com.jkqj.base.gateway.invoker.InvokerFactory;
import com.jkqj.base.gateway.router.Context;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public abstract class Upstream extends BaseHandler implements Handler {
    private String id;

    abstract public Upstreams getType();

    @Override
    public void handle(Context context) {
        var invoker = InvokerFactory.getInvoker(getType());
        invoker.doInvoke(context);
    }
}
