package com.ecom.config;

import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * ใน {@code app.auth.mode=dev} ให้ลิงก์ในอีเมล (รวมลิงก์เข้าสู่ระบบของผู้ลงนามภายนอก) ชี้มาที่เครื่องตัวเอง
 * แทน https://hrd.computing.kku.ac.th — ใช้ค่าเดียวกับ {@code app.esign.base-url} (ค่าเริ่มต้น https://localhost:8081)
 *
 * <p>มีผลเฉพาะเมื่อไม่ได้ตั้ง {@code app.public-base-url} เอง และโหมดเป็น dev เท่านั้น
 * production จึงยังได้ URL จริงตามเดิม
 */
public class DevPublicBaseUrlPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY = "app.public-base-url";
    static final String DEV_DEFAULT = "https://localhost:8081";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        String mode = env.getProperty("app.auth.mode", AuthModeProperties.MODE_DEV).trim().toLowerCase();
        if (!AuthModeProperties.MODE_DEV.equals(mode) || env.containsProperty(PROPERTY)) {
            return;
        }
        String local = env.getProperty("app.esign.base-url", DEV_DEFAULT);
        env.getPropertySources().addLast(new MapPropertySource("devPublicBaseUrl", Map.of(PROPERTY, local)));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
