package com.jkqj.base.gateway.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;

@Slf4j
public final class TokenUtils {
    public static final String IDENTIFIER = "id";
    private static final String PLATFORM = "plt";

    public static Optional<String> sign(TokenInfo info, String secret) {
        String token;

        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            token = JWT.create()
                    .withClaim(IDENTIFIER, info.getUid())
                    .withClaim(PLATFORM, info.getPlatform())
                    .withExpiresAt(info.getExpiresAt())
                    .sign(algorithm);
        } catch (Exception e) {
            log.error("can't sign [info: {}, secret: {}]", info, secret);
            token = null;
        }

        return Optional.ofNullable(token);
    }

    //TODO: only for backward compatibility, remove it before 330
    public static boolean verify(String token, Long uid, String secret) {
        return verify(token, uid, null, secret);
    }

    public static boolean verify(String token, Long uid, String platform, String secret) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            var verification = JWT.require(algorithm).withClaim(IDENTIFIER, uid);
            if (StringUtils.isNotBlank(platform)) {
                verification.withClaim(PLATFORM, platform);
            }
            var verifier = verification.build();

            DecodedJWT jwt = verifier.verify(token);
            Date expiresAt = jwt.getExpiresAt();
            return expiresAt != null && expiresAt.compareTo(new Date()) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static Optional<TokenInfo> parse(String token) {
        return parse(token, null);
    }

    //TODO: only for backward compatibility, remove platform parameter before 330
    public static Optional<TokenInfo> parse(String token, String platform) {
        try {
            DecodedJWT jwt = JWT.decode(token);
            Long identifier = jwt.getClaim(IDENTIFIER).asLong();

            var platformClaim = jwt.getClaim(PLATFORM);
            if (!platformClaim.isNull()) {
                platform = platformClaim.asString();
            }

            Date expiresAt = jwt.getExpiresAt();

            return Optional.ofNullable(TokenInfo.of(identifier, platform, expiresAt));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Value
    public static class TokenInfo {
        Long uid;
        String platform;
        Date expiresAt;

        public boolean isNotExpired() {
            return !isExpired();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt.getTime();
        }

        public static TokenInfo of(Long uid, String platform, Date expiresAt) {
            if (uid == null || uid <= 0) {
                return null;
            }

            return new TokenInfo(uid, platform, expiresAt);
        }

        public static TokenInfo of(Long uid, String platform, LocalDateTime expiresAt) {
            return of(uid, platform, Date.from(expiresAt.atZone(ZoneId.systemDefault()).toInstant()));
        }
    }
}
