package com.jkqj.base.gateway.middleware;

import com.google.common.base.Strings;
import com.jkqj.base.gateway.handler.BaseHandler;
import com.jkqj.base.gateway.router.Context;
import com.jkqj.base.gateway.router.Header;
import com.jkqj.base.gateway.rpc.client.RpcHelper;
import com.jkqj.base.gateway.utils.TokenUtils;
import com.jkqj.common.enums.BizType;
import com.jkqj.uc.client.params.LoginInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Date;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;
import static com.jkqj.base.gateway.rpc.client.RpcHelper.getUserRpcService;
import static java.lang.Long.parseLong;
import static java.util.Objects.nonNull;

@Slf4j
public class AuthenticationMiddleware extends BaseHandler implements Middleware {
    public static final String HEADER_TOKEN = "token";
    public static final String HEADER_PLATFORM = "platform";
    public static final String HEADER_UID = "uid";
    public static final String HEADER_OP_ID = "op-id";

    public static final int INVALID_TOKEN = 4002;
    static final int OPERATION_FAILED = 4008;
    static final int RUN_AS_NO_RIGHTS = 4009;
    static final int RUN_AS_NOT_EXISTS = 4007;
    static final int RUN_MODE_NO_RIGHTS = 4010;
    static final int NOT_SUBSCRIBING = 4020;
    static final String HEADER_BIZ_TYPES = "biz-types";
    static final String HEADER_COMPANY_ID = "company-id";
    static final String HEADER_RETA_TOKEN = "reta-token";
    static final String HEADER_TARGET_UID = "target-uid";
    static final String HEADER_RUN_AS = "run-as";
    static final String HEADER_RUN_MODE = "run_mode";
    static final int RUN_AS_SU_CONFLICT = 5001;

    @Override
    public Middlewares getType() {
        return Middlewares.AUTHENTICATION;
    }

    @Override
    public boolean preProcess(Context context) {
        var request = context.getRequest();
        var token = request.getHeader(HEADER_TOKEN);
        if (Strings.isNullOrEmpty(token)) {
            log.debug("token is empty");
            context.fail(INVALID_TOKEN, "Token为空");
            return false;
        }

        var tokenInfo = TokenUtils.parse(token);
        if (tokenInfo.isEmpty()) {
            log.debug("can't parse tokenInfo {}", token);
            context.fail(INVALID_TOKEN, "Token不合法");
            return false;
        }

        if (tokenInfo.get().getExpiresAt().before(new Date())) {
            log.debug("token has expired at {} {}", tokenInfo, token);
            context.fail(INVALID_TOKEN, "Token过期");
            return false;
        }

        var platform = request.getHeader(HEADER_PLATFORM);
        var opId = tokenInfo.get().getUid();

        var tokenPlatform = tokenInfo.get().getPlatform();
        if (StringUtils.isNotBlank(tokenPlatform)) {
            platform = tokenPlatform;
        }

        var verified = verifyLogin(token, opId, platform);
        if (!verified.getLeft()) {
            context.fail(INVALID_TOKEN, "Token无效");
            return false;
        }

        var loginInfo = verified.getRight();
        var uid = inferUid(opId, loginInfo);

        if (notMatchTargetUid(request.getHeader(HEADER_TARGET_UID), uid)) {
            context.fail(OPERATION_FAILED, "该附身已退出，请关闭页面");
            return false;
        }

        var runAs = request.getHeader(HEADER_RUN_AS);
        if (StringUtils.isNumeric(runAs)) {
            if (loginInfo.isSu()) {
                context.fail(RUN_AS_SU_CONFLICT, "附身和超级管理员模式冲突");
                return false;
            }

            var runAsUid = parseLong(runAs);
            if (!getUserRpcService().getUserInfoById(runAsUid).isSuccess()) {
                context.fail(RUN_AS_NOT_EXISTS, "被管理用户不存在");
                return false;
            }

            var isSuperuserResult = getUserRpcService().isSuperuser(opId);
            if (!isSuperuserResult.isSuccess() || !isSuperuserResult.getData()) {
                context.fail(RUN_AS_NO_RIGHTS, "当前登录用户无超级管理员权限");
                return false;
            }

            context.addHeader(new Header(HEADER_RUN_AS, runAs));
            uid = runAsUid;
        }

        var runMode = request.getHeader(HEADER_RUN_MODE);
        if (StringUtils.isNotBlank(runMode)) {
            if (!processRunMode(context, RunMode.of(runMode))) {
                context.fail(RUN_MODE_NO_RIGHTS, "不安全操作");
                return false;
            }
        }

        var verifySubscribe = RpcHelper.getOrderRpcService().isSubscribing(platform, uid);
        if (!verifySubscribe.isSuccess() || !verifySubscribe.getData()) {
            context.fail(NOT_SUBSCRIBING, "您未订阅我们的产品");
            return false;
        }

        var result = processContext(context, opId, uid, loginInfo, token);
        log.debug("token verified result:{}", result);

        return result;
    }

    private boolean processRunMode(Context context, RunMode runMode) {
        var httpMethod = context.getMethod();
        if (RunMode.READ.equals(runMode)) {
            if (httpMethod.matches("POST")) {
                return false;
            }
            if (httpMethod.matches("PUT")) {
                return false;
            }
            if (httpMethod.matches("DELETE")) {
                return false;
            }
        }
        return true;
    }

    private boolean processContext(Context context, Long opId, Long uid, LoginInfo loginInfo, String token) {
        log.debug("opId: {}, uid {}, loginInfo: {}", opId, uid, loginInfo);
        checkArgument(loginInfo != null);

        context.addHeader(new Header(HEADER_RETA_TOKEN, token));

        context.setOpId(opId);
        if (!Objects.equals(uid, opId)) {
            context.addHeader(new Header(HEADER_OP_ID, opId));
        }

        context.setUid(uid);
        context.addHeader(new Header(HEADER_UID, uid));

        var bizTypes = loginInfo.isSu() ? loginInfo.getSuBizType() : loginInfo.getBizTypes();
        context.addHeader(new Header(HEADER_BIZ_TYPES, bizTypes));

        if (BizType.hasType(bizTypes, BizType.Recruiter)) {
            var companyId = loginInfo.getSuCompanyId();
            if (!loginInfo.isSu()) {
                var result = RpcHelper.getBizUserRpcService().getByBid(uid);
                if (result.isSuccess() && CollectionUtils.isNotEmpty(result.getData())) {
                    companyId = result.getData().get(0).getCompanyId();
                }
            }
            if (nonNull(companyId)) {
                context.addHeader(new Header(HEADER_COMPANY_ID, companyId));
            }
        }

        return true;
    }

    private Long inferUid(Long opId, LoginInfo loginInfo) {
        return loginInfo.isSu() ? loginInfo.getSuUid() : opId;
    }

    private boolean notMatchTargetUid(String targetUid, Long uid) {
        return StringUtils.isNotBlank(targetUid)
                && (!StringUtils.isNumeric(targetUid) || parseLong(targetUid) != uid);
    }

    private Pair<Boolean, LoginInfo> verifyLogin(String token, Long uid, String platform) {
        var result = getUserRpcService().getLoginInfoByUid(uid, platform);
        if (!result.isSuccess()) {
            log.debug("user rpc failed {}", result);
            return Pair.of(false, null);
        }

        var login = result.getData();
        if (login == null) {
            log.debug("login is null");
            return Pair.of(false, null);
        }

        var key = login.getKey();
        if (!TokenUtils.verify(token, uid, key)) {
            log.debug("token verified fail {}, uid {}, platform {}, key {}",
                    token, uid, platform, key);
            return Pair.of(false, null);
        }

        if (login.isSu()) {
            log.info("login su as {}", login.getSuUid());
        }

        return Pair.of(true, login);
    }

    @Override
    public void postProcess(Context context) {
        context.getHeader(HEADER_RUN_AS)
                .ifPresent(header ->
                        context.getResponse()
                                .addHeader(HEADER_RUN_AS, header.getValueString()));
    }
}
