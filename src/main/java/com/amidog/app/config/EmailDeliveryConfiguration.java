package com.amidog.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class EmailDeliveryConfiguration {

    /**
     * A deliberately small, bounded post-commit boundary. Rejected tasks are handled by the
     * listener without retrying them on a request thread, so SMTP latency or saturation cannot
     * alter a public endpoint response.
     */
    @Bean(name = "emailDeliveryExecutor", destroyMethod = "shutdown")
    Executor emailDeliveryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("amidog-email-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }
}
