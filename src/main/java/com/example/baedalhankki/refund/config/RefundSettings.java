package com.example.baedalhankki.refund.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.time.Duration;

@ConfigurationProperties("app.refund")
public record RefundSettings(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("60s") Duration pollInterval,
        @DefaultValue("60s") Duration recoveryDelay,
        @DefaultValue("10m") Duration alertAfter,
        @DefaultValue("30m") Duration unpaidExpiry,
        @DefaultValue("14d") Duration automaticRetryLimit,
        @DefaultValue("60s") Duration leaseDuration,
        @DefaultValue("20") int batchSize,
        @DefaultValue("") String webhookSecurityKey) {
    public RefundSettings {
        if (pollInterval.isNegative() || pollInterval.isZero() || recoveryDelay.isNegative()
                || alertAfter.isNegative() || unpaidExpiry.isNegative() || unpaidExpiry.isZero()
                || automaticRetryLimit.isNegative() || automaticRetryLimit.isZero()
                || automaticRetryLimit.compareTo(Duration.ofDays(15)) >= 0
                || leaseDuration.compareTo(Duration.ofSeconds(30)) <= 0 || batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("Invalid refund timing settings");
        }
    }
}
