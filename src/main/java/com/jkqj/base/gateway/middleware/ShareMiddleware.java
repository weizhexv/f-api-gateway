package com.jkqj.base.gateway.middleware;

import com.jkqj.base.gateway.router.Context;
import com.jkqj.base.gateway.router.Header;
import com.jkqj.base.gateway.rpc.client.RpcHelper;
import com.jkqj.base.gateway.utils.TokenUtils;
import com.jkqj.common.enums.CommonErrorEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class ShareMiddleware extends AuthenticationMiddleware implements Middleware {
    private static final int SHARE_ACCESS_DENIED = 5006;

    private static final String RETA_TOKEN = "reta-token";
    protected static final String SVID = "svid";
    public static final String S_TOKEN = "s-token";

    @Override
    public Middlewares getType() {
        return Middlewares.SHARE;
    }

    @Override
    public boolean preProcess(Context context) {
        var request = context.getRequest();
        if (StringUtils.isNotBlank(request.getHeader(S_TOKEN))) {
            return processShareInfo(context);
        } else {
            return super.preProcess(context);
        }
    }

    public boolean processShareInfo(Context context) {
        var request = context.getRequest();
        var shareToken = request.getHeader(S_TOKEN);
        checkArgument(StringUtils.isNotBlank(shareToken));

        var cookieToken = getCookie(request, RETA_TOKEN);
        var sharedBy = TokenUtils.parse(cookieToken).map(TokenUtils.TokenInfo::getUid).orElse(null);
        var svid = getSvid(request);

        log.debug("checking share uri {}, s-token {}, reta-token {}, sharedBy {}, svid {}", context.getUri(), shareToken, cookieToken, sharedBy, svid);

        var infoResult = RpcHelper.getUserRpcService().checkShareToken(shareToken, sharedBy, svid);
        if (!infoResult.isSuccess() || infoResult.getData() == null) {
            context.fail(SHARE_ACCESS_DENIED);
            return false;
        }

        var info = infoResult.getData();
        log.debug("got share info {}", info);
        if (!info.isValid()) {
            context.fail(SHARE_ACCESS_DENIED);
            return false;
        }

        var contextInit = processContext(context, info.getUid(), cookieToken, info.getBizTypes());
        if (!contextInit) {
            log.error("init context failed {}", info);
            context.fail(CommonErrorEnum.SYSTEM_ERROR.getCode());
            return false;
        }

        return info.isValid();
    }

    private String getSvid(HttpServletRequest request) {
        var svid = getCookie(request, SVID);

        if (StringUtils.isBlank(svid)) {
            svid = request.getParameter(SVID);
        }

        if (StringUtils.isBlank(svid)) {
            svid = request.getHeader(SVID);
        }

        return svid;
    }

    private boolean processContext(Context context, Long uid, String cookieToken, Byte bizTypes) {
        context.setOpId(uid);
        context.setUid(uid);

        if (StringUtils.isNotBlank(cookieToken)) {
            context.addHeader(new Header(RETA_TOKEN, cookieToken));
        }
        context.addHeader(new Header(HEADER_UID, uid));
        context.addHeader(new Header(HEADER_BIZ_TYPES, bizTypes));

        var result = RpcHelper.getBizUserRpcService().getByBid(uid);
        if (result.isSuccess() && CollectionUtils.isNotEmpty(result.getData())) {
            var companyId = result.getData().get(0).getCompanyId();
            context.addHeader(new Header(HEADER_COMPANY_ID, companyId));
        } else {
            log.error("no comapy info found {}", uid);
            context.fail(SHARE_ACCESS_DENIED);
            return false;
        }

        return true;
    }

    private String getCookie(HttpServletRequest request, String name) {
        var cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        return Arrays.stream(cookies)
                .filter(it -> name.equalsIgnoreCase(it.getName()))
                .map(Cookie::getValue)
                .findFirst().orElse(null);
    }
}
