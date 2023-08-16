package com.jkqj.base.gateway.config;

import com.google.common.base.Strings;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

@Slf4j
@Value
public class RouteTrigger {
    long timestamp;

    public static Optional<RouteTrigger> from(String text) {
        log.info("constructing from {}", text);

        text = StringUtils.trimToNull(text);

        if (Strings.isNullOrEmpty(text) || !StringUtils.isNumeric(text)) {
            return Optional.empty();
        }

        long timestamp = Long.parseLong(text);
        if (timestamp == 0) {
            return Optional.empty();
        }

        return Optional.of(new RouteTrigger(timestamp));
    }

    public boolean after(RouteTrigger that) {
        return that == null || this.timestamp > that.timestamp;
    }
}
