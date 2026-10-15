package com.mcverse.jobify.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Switches on {@code @Scheduled} jobs (the notification clean-up). Set {@code app.scheduling.enabled=false} to turn them all off. */
@Configuration
@EnableScheduling
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
