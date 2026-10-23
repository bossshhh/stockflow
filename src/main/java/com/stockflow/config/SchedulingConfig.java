package com.stockflow.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on the @Scheduled housekeeping jobs; off with stockflow.housekeeping.enabled=false (as in tests). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "stockflow.housekeeping", name = "enabled", havingValue = "true", matchIfMissing = true)
class SchedulingConfig {
}
