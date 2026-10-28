package com.example.wallet.common;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.*}
 *
 * @param reportingZone   time zone for "today", "this month" and daily buckets. Storage is always UTC;
 *                        the zone only decides where a day starts when presenting data.
 * @param defaultCurrency currency for new wallets when none is given, and for single-currency summaries
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(ZoneId reportingZone, String defaultCurrency) {

    public AppProperties {
        if (reportingZone == null) {
            reportingZone = ZoneId.of("Asia/Kolkata");
        }
        if (defaultCurrency == null || defaultCurrency.isBlank()) {
            defaultCurrency = "INR";
        }
    }
}
