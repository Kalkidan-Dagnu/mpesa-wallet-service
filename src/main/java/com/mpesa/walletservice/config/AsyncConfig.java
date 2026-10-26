package com.mpesa.walletservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async executor configuration for the disbursement processor.
 *
 * ── Sizing rationale ─────────────────────────────────────────────────────────
 * Each batch runs on one thread. With pool-size = 4, up to 4 batches can
 * process concurrently. Each batch thread holds at most one DB connection at
 * a time (for the duration of a single row transaction), so total concurrent
 * connections from this pool = 4, well within the HikariCP pool-size of 10.
 *
 * Queue capacity = 20: if more than 4+20 batches arrive simultaneously the
 * caller gets a 202 and the batch stays PENDING; the processor will resume
 * when a thread is free. In production, replace this with a durable queue
 * (SQS, Kafka) so batches survive a JVM restart.
 */
@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean(name = "disbursementExecutor")
    public Executor disbursementExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("disbursement-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(65);  // >= 60s batch deadline
        executor.initialize();
        return executor;
    }
}
