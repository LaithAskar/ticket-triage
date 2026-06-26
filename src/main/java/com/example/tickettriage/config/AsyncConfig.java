package com.example.tickettriage.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Enables asynchronous processing and defines the bounded executor used for
 * triage. A bounded queue plus a caller-runs rejection policy applies natural
 * back-pressure instead of letting work pile up without limit.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "triageExecutor")
    public Executor triageExecutor(
            @Value("${async.triage.core-pool-size:2}") int corePoolSize,
            @Value("${async.triage.max-pool-size:8}") int maxPoolSize,
            @Value("${async.triage.queue-capacity:500}") int queueCapacity) {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("triage-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
