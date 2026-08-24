package com.ecom.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * When deferred work actually runs.
 *
 * <p>Status-change notifications are raised inside the transaction that makes
 * the change. Sending them straight away means the background thread can read
 * the row before the new status is committed — and if the transaction rolls
 * back, it has told the applicant about something that never happened.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:aftercommitdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "server.ssl.enabled=false"
})
@DisplayName("งานแจ้งเตือนต้องยิงหลัง commit เท่านั้น")
class AfterCommitRunnerTest {

    @Autowired
    private AfterCommitRunner afterCommit;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("ไม่มี transaction — ทำทันที ไม่ต้องรออะไร")
    void runsImmediatelyOutsideATransaction() {
        AtomicInteger ran = new AtomicInteger();

        afterCommit.run(ran::incrementAndGet);

        assertThat(ran.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("อยู่ใน transaction — ต้องยังไม่ทำจนกว่าจะ commit เสร็จ")
    void waitsForTheCommit() {
        AtomicInteger ran = new AtomicInteger();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(status -> {
            afterCommit.run(ran::incrementAndGet);
            assertThat(ran.get())
                    .as("ยิงก่อน commit จะทำให้เธรดเบื้องหลังอ่านสถานะเก่า")
                    .isZero();
        });

        assertThat(ran.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("transaction ถูก rollback — ต้องไม่แจ้งเตือนเรื่องที่ไม่ได้เกิดขึ้น")
    void doesNotRunWhenTheTransactionRollsBack() {
        AtomicInteger ran = new AtomicInteger();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(status -> {
            afterCommit.run(ran::incrementAndGet);
            status.setRollbackOnly();
        });

        assertThat(ran.get()).isZero();
    }

    @Test
    @DisplayName("แจ้งเตือนพังต้องไม่ลาม — ข้อมูล commit ไปแล้ว")
    void aFailedNotificationDoesNotBecomeAFailedRequest() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // ต้องไม่โยน exception ออกมาจาก commit
        tx.executeWithoutResult(status -> afterCommit.run(() -> {
            throw new IllegalStateException("mail server exploded");
        }));

        afterCommit.run(() -> {
            throw new IllegalStateException("mail server exploded");
        });
    }
}
