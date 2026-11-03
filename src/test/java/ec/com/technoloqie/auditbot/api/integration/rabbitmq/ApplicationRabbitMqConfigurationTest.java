package ec.com.technoloqie.auditbot.api.integration.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Verifica que la conexión al broker RabbitMQ se obtiene de las propiedades
 * estándar {@code spring.rabbitmq.*} sin credenciales hardcodeadas en código,
 * y que los settings del listener container y su retry se bindean correctamente.
 * Cubre R23 (US-01), R3, R4, R5, R45 (US-02).
 */
class ApplicationRabbitMqConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(EmptyConfig.class)
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    RabbitAutoConfiguration.class, PropertyPlaceholderAutoConfiguration.class));

    @Test
    void shouldBindBrokerConnectionFromEnvironment() {
        contextRunner
                .withPropertyValues(
                        "spring.rabbitmq.host=test-broker-host",
                        "spring.rabbitmq.port=15672",
                        "spring.rabbitmq.username=test-user",
                        "spring.rabbitmq.password=test-pass")
                .run(context -> {
                    org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory =
                            context.getBean(org.springframework.amqp.rabbit.connection.ConnectionFactory.class);
                    assertThat(connectionFactory.getHost()).isEqualTo("test-broker-host");
                    assertThat(connectionFactory.getPort()).isEqualTo(15672);
                });
    }

    @Test
    void shouldBindListenerContainerSettings() {
        contextRunner
                .withPropertyValues(
                        "spring.rabbitmq.host=localhost",
                        "spring.rabbitmq.listener.simple.acknowledge-mode=auto",
                        "spring.rabbitmq.listener.simple.default-requeue-rejected=false",
                        "spring.rabbitmq.listener.simple.prefetch=1",
                        "spring.rabbitmq.listener.simple.concurrency=2",
                        "spring.rabbitmq.listener.simple.max-concurrency=4")
                .run(context -> {
                    org.springframework.boot.amqp.autoconfigure.RabbitProperties rabbitProperties =
                            context.getBean(org.springframework.boot.amqp.autoconfigure.RabbitProperties.class);
                    assertThat(rabbitProperties.getListener().getSimple().getAcknowledgeMode())
                            .isEqualTo(org.springframework.amqp.core.AcknowledgeMode.AUTO);
                    assertThat(rabbitProperties.getListener().getSimple().getDefaultRequeueRejected())
                            .isFalse();
                    assertThat(rabbitProperties.getListener().getSimple().getPrefetch())
                            .isEqualTo(1);
                    assertThat(rabbitProperties.getListener().getSimple().getConcurrency())
                            .isEqualTo(2);
                    assertThat(rabbitProperties.getListener().getSimple().getMaxConcurrency())
                            .isEqualTo(4);
                });
    }

    @Test
    void shouldOverrideListenerContainerSettings() {
        contextRunner
                .withPropertyValues(
                        "spring.rabbitmq.host=localhost",
                        "spring.rabbitmq.listener.simple.prefetch=5",
                        "spring.rabbitmq.listener.simple.concurrency=3",
                        "spring.rabbitmq.listener.simple.max-concurrency=8")
                .run(context -> {
                    org.springframework.boot.amqp.autoconfigure.RabbitProperties rabbitProperties =
                            context.getBean(org.springframework.boot.amqp.autoconfigure.RabbitProperties.class);
                    assertThat(rabbitProperties.getListener().getSimple().getPrefetch()).isEqualTo(5);
                    assertThat(rabbitProperties.getListener().getSimple().getConcurrency()).isEqualTo(3);
                    assertThat(rabbitProperties.getListener().getSimple().getMaxConcurrency()).isEqualTo(8);
                });
    }

    @Test
    void shouldBindListenerRetrySettings() {
        contextRunner
                .withPropertyValues(
                        "spring.rabbitmq.host=localhost",
                        "spring.rabbitmq.listener.simple.retry.enabled=true",
                        "spring.rabbitmq.listener.simple.retry.max-retries=3",
                        "spring.rabbitmq.listener.simple.retry.initial-interval=1000ms",
                        "spring.rabbitmq.listener.simple.retry.multiplier=2",
                        "spring.rabbitmq.listener.simple.retry.max-interval=10000ms")
                .run(context -> {
                    org.springframework.boot.amqp.autoconfigure.RabbitProperties rabbitProperties =
                            context.getBean(org.springframework.boot.amqp.autoconfigure.RabbitProperties.class);
                    assertThat(rabbitProperties.getListener().getSimple().getRetry().isEnabled()).isTrue();
                    assertThat(rabbitProperties.getListener().getSimple().getRetry().getMaxRetries()).isEqualTo(3);
                    assertThat(rabbitProperties.getListener().getSimple().getRetry().getMultiplier()).isEqualTo(2.0);
                });
    }

    @Configuration
    static class EmptyConfig {
    }
}
