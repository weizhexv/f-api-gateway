package com.jkqj.base.gateway.middleware;

import com.jkqj.base.gateway.router.Context;
import com.jkqj.base.gateway.router.Header;
import com.jkqj.base.gateway.rpc.client.RpcHelper;
import com.jkqj.base.gateway.utils.TokenUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Date;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkState;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * @author xuweizhe@reta-inc.com
 * @date 2022/4/11
 * @description
 */
@Slf4j
public class GuestMiddleware extends AuthenticationMiddleware implements Middleware {
    private static final String TEMP_TOKEN = "temp-token";

    @Override
    public Middlewares getType() {
        return Middlewares.TEMPORARY_TOKEN;
    }

    @Override
    public boolean preProcess(Context context) {
        if (isGuest(context)) {
            return processGuestInfo(context);
        } else {
            return super.preProcess(context);
        }
    }

    private boolean isGuest(Context context) {
        var request = context.getRequest();

        var tempToken = request.getParameter(TEMP_TOKEN);
        if (isNotBlank(tempToken)) {
            return true;
        }

        tempToken = request.getHeader(TEMP_TOKEN);

        return isNotBlank(tempToken);
    }

    private boolean processGuestInfo(Context context) {
        var request = context.getRequest();

        var tempToken = request.getParameter(TEMP_TOKEN);
        if (isBlank(tempToken)) {
            tempToken = request.getHeader(TEMP_TOKEN);
        }

        checkState(isNotBlank(tempToken));

        var tempTokenInfoOpt = TokenUtils.parse(tempToken);
        if (tempTokenInfoOpt.isEmpty()) {
            context.fail(INVALID_TOKEN, "临时身份失效");
            return false;
        }

        var tempTokenInfo = tempTokenInfoOpt.get();
        if (tempTokenInfo.getExpiresAt().before(new Date())) {
            log.debug("temp token has expired at {}, temp token info {}", tempTokenInfo.getExpiresAt(), tempTokenInfo);
            context.fail(INVALID_TOKEN, "临时身份过期");
            return false;
        }

        var uid = tempTokenInfo.getUid();
        var pair = verifyTempToken(tempToken, uid);
        if (!pair.getLeft()) {
            log.debug("temp token verify failed {}", pair);
            context.fail(INVALID_TOKEN, pair.getRight());
            return false;
        }

        Long companyId = null;
        var result = RpcHelper.getBizUserRpcService().getByBid(uid);
        if (result.isSuccess() && CollectionUtils.isNotEmpty(result.getData())) {
            companyId = result.getData().get(0).getCompanyId();
        }

        context.addHeader(new Header(TEMP_TOKEN, tempToken));
        context.setUid(uid);
        context.addHeader(new Header(HEADER_UID, uid));
        if (Objects.nonNull(companyId)) {
            context.addHeader(new Header(HEADER_COMPANY_ID, companyId));
        }

        return true;
    }

    private Pair<Boolean, String> verifyTempToken(String tempToken, Long uid) {
        var result = RpcHelper.getUserRpcService().getUserSeedById(uid);
        if (!result.isSuccess()) {
            return Pair.of(false, "用户不存在");
        }

        var seed = result.getData();
        if (isBlank(seed)) {
            return Pair.of(false, "用户不存在");
        }

        if (!TokenUtils.verify(tempToken, uid, seed)) {
            return Pair.of(false, "非法用户请求");
        }

        return Pair.of(true, "");
    }

    @Override
    public void postProcess(Context context) {
        if (!isGuest(context)) {
            super.postProcess(context);
        }
    }
}
