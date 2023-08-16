package com.jkqj.base.gateway.monitor;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jkqj.base.gateway.middleware.AuthenticationMiddleware;
import com.jkqj.base.gateway.router.Context;
import io.micrometer.core.instrument.Tag;
import lombok.Data;

import java.util.List;
import java.util.Objects;

import static org.apache.commons.lang3.StringUtils.defaultString;

/**
 * @author xuweizhe@reta-inc.com
 * @date 2022/2/15
 * @description
 */
@Data
public class MetricTags {
    private String url;
    private String result;
    private String platform;
    private String upstream;
    private String code;
    private String exp;

    public static List<Tag> toList(MetricTags tags) {
        Preconditions.checkArgument(Objects.nonNull(tags));
        List<Tag> result = Lists.newArrayList();

        result.add(Tag.of("url", defaultString(tags.getUrl())));
        result.add(Tag.of("result", defaultString(tags.getResult())));
        result.add(Tag.of("platform", defaultString(tags.getPlatform())));
        result.add(Tag.of("upstream", defaultString(tags.getUpstream())));
        result.add(Tag.of("code", defaultString(tags.getCode())));
        result.add(Tag.of("exp", defaultString(tags.getExp())));

        return result;
    }

    public static MetricTags of(Context context) {
        var request = context.getRequest();
        var platform = request.getHeader(AuthenticationMiddleware.HEADER_PLATFORM);
        var result = context.getResult().isSuccess();
        String upstream = null;
        try {
            upstream = context.getRoute().getProxyPass().getUpstream().getType().name();
        } catch (Exception ignore) {
        }
        var code = context.getResult().getCode();

        MetricTags metricTags = new MetricTags();
        metricTags.setUrl(context.getUri());
        metricTags.setPlatform(platform);
        metricTags.setResult(String.valueOf(result));
        metricTags.setUpstream(upstream);
        metricTags.setCode(String.valueOf(code));

        return metricTags;
    }
}
