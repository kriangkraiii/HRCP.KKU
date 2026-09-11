package com.ecom.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;

/**
 * Verifies that Java 21+ Virtual Threads (Project Loom) are properly enabled
 * across Spring Boot components (TaskExecutors, Concurrency, and HikariCP connection handling).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:vthreaddb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "server.ssl.enabled=false",
        "spring.threads.virtual.enabled=true"
})
@DisplayName("Virtual Threads Configuration & Concurrency Verification")
class VirtualThreadsConfigTest {

    @Autowired
    private Environment environment;

    @Autowired
    private AsyncConfig asyncConfig;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("spring.threads.virtual.enabled ต้องเปิดใช้งานเป็น true ใน Environment")
    void virtualThreadsPropertyIsEnabled() {
        String enabled = environment.getProperty("spring.threads.virtual.enabled");
        assertThat(enabled)
                .as("spring.threads.virtual.enabled must be explicitly true")
                .isEqualTo("true");
    }

    @Test
    @DisplayName("AsyncConfig.getAsyncExecutor() รันงานบน Java 21 Virtual Thread จริง (isVirtual == true)")
    void asyncExecutorUsesVirtualThreads() throws Exception {
        Executor asyncExecutor = asyncConfig.getAsyncExecutor();
        assertThat(asyncExecutor)
                .as("AsyncConfig.getAsyncExecutor() must not be null")
                .isNotNull();

        CompletableFuture<Boolean> isVirtualFuture = new CompletableFuture<>();
        CompletableFuture<String> threadNameFuture = new CompletableFuture<>();

        asyncExecutor.execute(() -> {
            Thread current = Thread.currentThread();
            isVirtualFuture.complete(current.isVirtual());
            threadNameFuture.complete(current.getName());
        });

        Boolean isVirtual = isVirtualFuture.get(5, TimeUnit.SECONDS);
        String threadName = threadNameFuture.get(5, TimeUnit.SECONDS);

        assertThat(isVirtual)
                .as("Task executed on asyncExecutor must be on a Java 21 Virtual Thread")
                .isTrue();

        assertThat(threadName)
                .as("Thread name should identify the task execution")
                .isNotBlank();
    }

    @Test
    @DisplayName("รองรับการรัน Database Query พร้อมกัน 50 Virtual Threads โดย HikariCP จัดการคิวได้ราบรื่น")
    void concurrentVirtualThreadsDatabaseAccess() {
        int threadCount = 50;
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        try (ExecutorService vExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threadCount; i++) {
                futures.add(CompletableFuture.runAsync(() -> {
                    assertThat(Thread.currentThread().isVirtual())
                            .as("Worker thread must be a Virtual Thread")
                            .isTrue();

                    try (Connection conn = dataSource.getConnection();
                         PreparedStatement ps = conn.prepareStatement("SELECT 1");
                         ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getInt(1)).isEqualTo(1);
                    } catch (Exception e) {
                        throw new RuntimeException("Database query failed on virtual thread", e);
                    }
                }, vExecutor));
            }

            // Wait for all 50 virtual threads to finish their DB operations
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
    }
}
