package com.ecom.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ThreadPoolExecutor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@DisplayName("งานแปลง PDF ล่วงหน้า — executor หลาย thread และการเก็บเอกสารที่ลงนามแยก executor")
class PrewarmExecutorTest {

    private final AsyncConfig config = new AsyncConfig(null, null);

    @Test
    @DisplayName("prewarm ใช้ 2–4 thread (โควตา LibreOffice คุมที่ DocumentPrewarmService)")
    void prewarmUsesTwoToFourThreads() {
        ThreadPoolTaskExecutor prewarm = (ThreadPoolTaskExecutor) config.docPrewarmExecutor();
        try {
            assertThat(prewarm.getCorePoolSize()).isEqualTo(2);
            assertThat(prewarm.getMaxPoolSize()).isEqualTo(4);
        } finally {
            prewarm.shutdown();
        }
    }

    @Test
    @DisplayName("การเก็บเอกสารที่ลงนามแล้วมี executor ของตัวเอง และไม่ทิ้งงานเมื่อคิวเต็ม")
    void signedArchivingHasItsOwnExecutor() {
        ThreadPoolTaskExecutor archive = (ThreadPoolTaskExecutor) config.signedArchiveExecutor();
        try {
            assertThat(archive.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
        } finally {
            archive.shutdown();
        }
    }
}
