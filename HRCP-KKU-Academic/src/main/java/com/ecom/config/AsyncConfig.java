package com.ecom.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

/**
 * Enables async execution and defines thread pools for background tasks.
 * The auditLogExecutor is used by AdminLogService to persist audit logs
 * without blocking HTTP request threads.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /**
     * The executor behind plain {@code @Async}, carrying the security context
     * across the thread hop.
     *
     * <p>Almost every notification is sent from an {@code @Async} method, and
     * a fresh thread has no security context of its own. Without this the mail
     * guard cannot tell who set a notification off, and so cannot hold back the
     * ones raised while rehearsing with a test account.
     *
     * <p>The delegate is a {@link SimpleAsyncTaskExecutor} because that is what
     * {@code @Async} already used here: Spring Boot only contributes its pooled
     * {@code applicationTaskExecutor} when the application declares no executor
     * of its own, and this class declares two. Substituting a pool instead
     * would quietly change how much notification work runs in parallel, which
     * is not something this change has any business altering.
     */
    @Override
    public java.util.concurrent.Executor getAsyncExecutor() {
        AsyncTaskExecutor delegate = applicationTaskExecutor
                .getIfAvailable(() -> new SimpleAsyncTaskExecutor("task-"));
        return new DelegatingSecurityContextAsyncTaskExecutor(delegate);
    }

    private final ObjectProvider<AsyncTaskExecutor> applicationTaskExecutor;

    public AsyncConfig(
            @Qualifier("applicationTaskExecutor") ObjectProvider<AsyncTaskExecutor> applicationTaskExecutor) {
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    /**
     * @param async when false, audit-log writes run on the calling thread. Tests
     *              rely on this so they can assert on the row straight after the
     *              call instead of racing the pool; it also lets an operator make
     *              audit logging synchronous if losing an entry is unacceptable.
     */
    @Bean("auditLogExecutor")
    public Executor auditLogExecutor(
            @org.springframework.beans.factory.annotation.Value("${app.audit-log.async:true}") boolean async) {
        if (!async) {
            return Runnable::run;
        }

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("audit-log-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * Dedicated executor for background PDF pre-generation and cache warming.
     * Runs at slightly reduced thread priority so user-facing web requests have precedence.
     */
    @Bean("docPrewarmExecutor")
    public Executor docPrewarmExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("doc-prewarm-");
        executor.setThreadPriority(Thread.NORM_PRIORITY - 1);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.initialize();
        return executor;
    }
}
