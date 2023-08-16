package com.jkqj.base.gateway.middleware;

import com.jkqj.base.gateway.handler.BaseHandler;
import com.jkqj.base.gateway.router.Context;
import com.jkqj.base.gateway.router.Header;
import com.jkqj.base.gateway.rpc.client.RpcHelper;
import com.jkqj.uc.client.params.AuthRequest;
import com.jkqj.uc.client.params.RoleInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class AuthorizationMiddleware extends BaseHandler implements Middleware {
    private static final String HEADER_ROLES = "roles";

    @Override
    public Middlewares getType() {
        return Middlewares.AUTHORIZATION;
    }

    @Override
    public boolean preProcess(Context context) {
        var route = context.getRoute();
        var roles = splitGroup(route.getRoles());
        var permissions = splitGroup(route.getPermissions());

        processRoleHeaders(context);

        if (roles == null && permissions == null) {
            return true;
        }

        var uid = context.getUid();
        log.debug("authorizing with uid {}, roles {}, permissions {}", uid, roles, permissions);

        if (uid == null || uid <= 0) {
            log.error("empty uid");
            return false;
        }

        if (!checkAuthorization(uid, roles, permissions)) {
            log.error("check authorization fail");
            return false;
        }

        return true;
    }

    private void processRoleHeaders(Context context) {
        if (Objects.isNull(context.getUid())){
            return;
        }

        var result = RpcHelper.getAuthRpcService().getRolesByUid(context.getUid());

        log.info("get roles by uid {} result {}", context.getUid(), result);

        if (result.isSuccess()) {
            var roleInfos = result.getData();
            if (CollectionUtils.isNotEmpty(roleInfos)) {
                var codes = roleInfos.stream().map(RoleInfo::getCode).collect(Collectors.toSet());
                context.addHeader(new Header(HEADER_ROLES, codes));
            }
        }
    }

    private Pair<List<String>, List<String>> splitGroup(Collection<String> names) {
        if (CollectionUtils.isEmpty(names)) {
            return null;
        }

        var needList = new ArrayList<String>();
        var notList = new ArrayList<String>();

        for (var name : names) {
            if (name.charAt(0) == '!') {
                notList.add(name);
            } else {
                needList.add(name);
            }
        }

        return Pair.of(needList, notList);
    }

    private boolean checkAuthorization(Long uid, Pair<List<String>, List<String>> roles, Pair<List<String>, List<String>> permissions) {
        var authzClient = RpcHelper.getAuthRpcService();

        List<String> needRoles = Collections.emptyList();
        List<String> notRoles = Collections.emptyList();
        if (roles != null) {
            needRoles = roles.getLeft();
            notRoles = roles.getRight();
        }

        List<String> needPermissions = Collections.emptyList();
        List<String> notPermissions = Collections.emptyList();
        if (permissions != null) {
            needPermissions = permissions.getLeft();
            notPermissions = permissions.getRight();
        }

        var request = new AuthRequest(uid, needRoles, notRoles, needPermissions, notPermissions);
        var result = authzClient.checkAuthorization(request);
        log.debug("check authorization result {}", result);

        return result.isSuccess() && BooleanUtils.isTrue(result.getData());
    }
}
