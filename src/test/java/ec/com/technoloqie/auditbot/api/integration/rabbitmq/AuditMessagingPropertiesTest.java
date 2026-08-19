package ec.com.technoloqie.auditbot.api.integration.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * Pruebas de binding de {@link AuditMessagingProperties} mediante
 * {@link ApplicationContextRunner}. Cubre R21 y R22: valores por defecto y
 * overrides del timeout de confirm y del tamaño máximo de payload.
 */
class AuditMessagingPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class, PropertyPlaceholderAutoConfiguration.class);

    @Test
    void shouldUseFiveSecondDefaultConfirmTimeout() {
        contextRunner.run(context -> {
            AuditMessagingProperties properties = context.getBean(AuditMessagingProperties.class);
            assertThat(properties.getConfirmTimeout()).isEqualTo(Duration.ofSeconds(5));
        });
    }

    @Test
    void shouldBindCustomConfirmTimeout() {
        contextRunner.withPropertyValues("auditbot.messaging.confirm-timeout=10s").run(context -> {
            AuditMessagingProperties properties = context.getBean(AuditMessagingProperties.class);
            assertThat(properties.getConfirmTimeout()).isEqualTo(Duration.ofSeconds(10));
        });
    }

    @Test
    void shouldUseOneMegabyteDefaultPayloadLimit() {
        contextRunner.run(context -> {
            AuditMessagingProperties properties = context.getBean(AuditMessagingProperties.class);
            assertThat(properties.getMaxPayloadSize()).isEqualTo(DataSize.ofMegabytes(1));
        });
    }

    @Test
    void shouldBindCustomPayloadLimit() {
        contextRunner.withPropertyValues("auditbot.messaging.max-payload-size=2MB").run(context -> {
            AuditMessagingProperties properties = context.getBean(AuditMessagingProperties.class);
            assertThat(properties.getMaxPayloadSize()).isEqualTo(DataSize.ofMegabytes(2));
        });
    }

    @Configuration
    @EnableConfigurationProperties(AuditMessagingProperties.class)
    static class TestConfig {
    }
}
