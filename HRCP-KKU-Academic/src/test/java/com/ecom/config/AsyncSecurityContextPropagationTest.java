package com.ecom.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;
import org.springframework.test.context.TestPropertySource;

/**
 * That {@code @Async} work still knows who set it off.
 *
 * <p>Notifications are almost all sent from {@code @Async} methods, and the
 * mail guard decides what to hold back from the signed-in user. Pool threads
 * carry no security context of their own, so without the wrapper below the
 * guard would see nobody and quietly send a rehearsal's email to real staff.
 * Wiring it up is easy to lose to a refactor and impossible to notice by eye,
 * hence this test.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:asyncpropdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "server.ssl.enabled=false"
})
@DisplayName("งาน @Async ต้องรู้ว่าใครเป็นคนสั่ง")
class AsyncSecurityContextPropagationTest {

    @Autowired
    private AsyncConfig asyncConfig;

    @Test
    @DisplayName("ตัวรัน @Async ถูกห่อให้ส่ง SecurityContext ข้ามเธรด")
    void asyncExecutorCarriesTheSecurityContext() {
        assertThat(asyncConfig.getAsyncExecutor())
                .as("ถ้าไม่ได้ห่อ ตัวกันอีเมลจะมองไม่เห็นว่าใครเป็นคนกด "
                        + "แล้วการซ้อมด้วยบัญชีทดสอบจะยิงเมลจริงออกไปหาคนจริง")
                .isInstanceOf(DelegatingSecurityContextAsyncTaskExecutor.class);
    }
}
