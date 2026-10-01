package ec.com.technoloqie.auditbot.api.integration.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Verifica que la conexión al broker RabbitMQ se obtiene de las propiedades
 * estándar {@code spring.rabbitmq.*} sin credenciales hardcodeadas en código.
 * Cubre R23.
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

    @Configuration
    static class EmptyConfig {
    }
}
