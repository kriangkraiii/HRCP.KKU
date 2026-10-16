package com.ecom.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * งานตั้งเวลาทุกงานต้องระบุเขตเวลาเอง
 *
 * <p>ไม่ระบุ zone = ใช้เขตเวลาตั้งต้นของ JVM ณ ตอนที่ bean นั้นถูกสร้าง {@code WebConfig} ตั้งเป็น Asia/Bangkok
 * ใน {@code @PostConstruct} ซึ่งไม่รับประกันว่าจะมาก่อน — บนเครื่องที่เป็น UTC อีเมลเตือนลงนาม 08:30 ออกตอน 15:30
 */
@DisplayName("งานตั้งเวลาทุกงานระบุ zone")
class ScheduledZoneTest {

    @Test
    @DisplayName("ทุก @Scheduled(cron) มี zone = Asia/Bangkok")
    void everyCronJobPinsItsZone() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        List<String> missing = new ArrayList<>();
        int crons = 0;
        for (BeanDefinition bean : scanner.findCandidateComponents("com.ecom")) {
            for (Method method : Class.forName(bean.getBeanClassName()).getDeclaredMethods()) {
                Scheduled scheduled = method.getAnnotation(Scheduled.class);
                if (scheduled == null || scheduled.cron().isEmpty()) {
                    continue;
                }
                crons++;
                if (!"Asia/Bangkok".equals(scheduled.zone())) {
                    missing.add(bean.getBeanClassName() + "#" + method.getName());
                }
            }
        }
        assertThat(crons).as("ต้องเจองานตั้งเวลา ไม่อย่างนั้นเทสนี้ไม่ได้ตรวจอะไร").isGreaterThan(5);
        assertThat(missing).isEmpty();
    }
}
