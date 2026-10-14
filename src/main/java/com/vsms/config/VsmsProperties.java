package com.vsms.config;

import java.math.BigDecimal;
import java.time.LocalTime;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Typed view of the {@code vsms.*} section of application.yml. */
@ConfigurationProperties(prefix = "vsms")
public record VsmsProperties(
        @DefaultValue Admin admin,
        @DefaultValue SampleData sampleData,
        @DefaultValue Billing billing,
        @DefaultValue Business business,
        @DefaultValue Appointments appointments) {

    public record Admin(
            @DefaultValue("admin") String username,
            @DefaultValue("") String password,
            @DefaultValue("false") boolean resetOnStartup) {
    }

    public record SampleData(@DefaultValue("false") boolean enabled) {
    }

    public record Billing(
            @DefaultValue("18.00") BigDecimal defaultTaxPercent,
            @DefaultValue("₹") String currencySymbol) {
    }

    public record Business(
            @DefaultValue("Apex Auto Care Service Center") String name,
            @DefaultValue("") String address,
            @DefaultValue("") String phone,
            @DefaultValue("") String email,
            @DefaultValue("") String taxNumber) {
    }

    public record Appointments(
            @DefaultValue("09:00") LocalTime openingTime,
            @DefaultValue("18:00") LocalTime closingTime,
            @DefaultValue("30") int slotMinutes,
            @DefaultValue("180") int maxDaysAhead) {
    }
}
