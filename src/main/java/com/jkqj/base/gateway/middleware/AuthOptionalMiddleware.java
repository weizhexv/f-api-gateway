package com.jkqj.base.gateway.middleware;

import com.google.common.collect.Sets;
import com.jkqj.base.gateway.router.Context;
import com.jkqj.base.gateway.utils.TokenUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Set;

public class AuthOptionalMiddleware extends AuthenticationMiddleware implements Middleware {
    @Override
    public boolean preProcess(Context context) {
        //TODO:临时解决090版本发送验证码传错误token问题
        if (isSkipUri(context)) {
            return true;
        }
        var token = context.getRequest().getHeader(HEADER_TOKEN);
        if (StringUtils.isBlank(token)) {
            return true;
        }

        var tokenInfo = TokenUtils.parse(token);
        if (tokenInfo.isEmpty()) {
            return true;
        }

        if (tokenInfo.get().isExpired()) {
            return true;
        }
        return super.preProcess(context);
    }

    private static final Set<String> SKIP_URIS = Sets.newHashSet("/qj/v1/auth/token/send", "/qj/v1/auth/token/verify");

    private boolean isSkipUri(Context context) {
        if (StringUtils.isBlank(context.getUri())) {
            return false;
        }
        return SKIP_URIS.contains(context.getUri());
    }


}
