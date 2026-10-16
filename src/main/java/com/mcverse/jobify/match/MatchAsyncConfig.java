package com.mcverse.jobify.match;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * A small separate pool for "new job" notifications. When it is full the work is dropped with a warning, never run on
 * (or blocking) the thread that posted the job.
 */
@Configuration
@EnableAsync
class MatchAsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(MatchAsyncConfig.class);

    @Bean(name = "matchNotifyExecutor")
    Executor matchNotifyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("match-notify-");
        executor.setRejectedExecutionHandler((task, pool) ->
                log.warn("New-match notifications are backed up; one job was skipped"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
