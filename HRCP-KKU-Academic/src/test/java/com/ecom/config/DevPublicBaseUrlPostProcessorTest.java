package com.ecom.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class DevPublicBaseUrlPostProcessorTest {

    private final DevPublicBaseUrlPostProcessor processor = new DevPublicBaseUrlPostProcessor();

    private StandardEnvironment env(Map<String, Object> props) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", props));
        return env;
    }

    @Test
    void isRegisteredWithSpringBoot() throws Exception {
        Properties factories = new Properties();
        try (var in = getClass().getResourceAsStream("/META-INF/spring.factories")) {
            factories.load(in);
        }
        assertThat(factories.getProperty(EnvironmentPostProcessor.class.getName()))
                .contains(DevPublicBaseUrlPostProcessor.class.getName());
    }

    @Test
    void devModeUsesLocalEsignBaseUrl() {
        StandardEnvironment env = env(Map.of("app.auth.mode", "dev", "app.esign.base-url", "https://localhost:9443"));

        processor.postProcessEnvironment(env, null);

        assertThat(env.getProperty("app.public-base-url")).isEqualTo("https://localhost:9443");
    }

    @Test
    void devModeWithoutEsignBaseUrlFallsBackToLocalhost() {
        StandardEnvironment env = env(Map.of("app.auth.mode", "dev"));

        processor.postProcessEnvironment(env, null);

        assertThat(env.getProperty("app.public-base-url")).isEqualTo("https://localhost:8081");
    }

    @Test
    void productionModeLeavesPublicBaseUrlUnset() {
        StandardEnvironment env = env(Map.of("app.auth.mode", "production", "app.esign.base-url", "https://localhost:8081"));

        processor.postProcessEnvironment(env, null);

        assertThat(env.getProperty("app.public-base-url")).isNull();
    }

    @Test
    void explicitPublicBaseUrlWinsInDevMode() {
        StandardEnvironment env = env(Map.of("app.auth.mode", "dev", "app.public-base-url", "https://staging.example"));

        processor.postProcessEnvironment(env, null);

        assertThat(env.getProperty("app.public-base-url")).isEqualTo("https://staging.example");
    }
}
