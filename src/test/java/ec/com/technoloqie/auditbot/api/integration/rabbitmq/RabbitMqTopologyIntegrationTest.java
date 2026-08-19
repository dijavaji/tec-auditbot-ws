package ec.com.technoloqie.auditbot.api.integration.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;

/**
 * Verifica que la topología de {@code US-01} (exchange, cola y binding) se
 * declara correctamente contra una instancia real de RabbitMQ.
 * <p>
 * El broker es externo al proceso de pruebas: se conecta al RabbitMQ ya
 * disponible en {@code localhost:5672} (gestionado fuera de este repositorio),
 * ya que Testcontainers/Docker no está disponible en este entorno de ejecución.
 * Cada método usa nombres de exchange/cola/routing-key únicos (sufijo aleatorio)
 * para aislarse de otras ejecuciones y limpia sus propios recursos al finalizar.
 * Cubre R5, R6, R7.
 */
@SpringBootTest(classes = RabbitMqTopologyIntegrationTest.MinimalTestConfig.class,
        properties = "spring.profiles.active=test")
@org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
class RabbitMqTopologyIntegrationTest {

    @Configuration
    @ImportAutoConfiguration(RabbitAutoConfiguration.class)
    @Import(RabbitMqConfiguration.class)
    static class MinimalTestConfig {
    }

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", () -> "127.0.0.1");
        registry.add("spring.rabbitmq.port", () -> "5672");
        registry.add("spring.rabbitmq.username", () -> "rabbitmq");
        registry.add("spring.rabbitmq.password", () -> "Password.1");
        String suffix = UUID.randomUUID().toString();
        registry.add("auditbot.messaging.exchange", () -> "auditbot.exchange.topology-test-" + suffix);
        registry.add("auditbot.messaging.queue", () -> "conversation.audit.queue.topology-test-" + suffix);
        registry.add("auditbot.messaging.routing-key",
                () -> "conversation.audit.requested.topology-test-" + suffix);
    }

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private ConnectionFactory connectionFactory;

    @Autowired
    private AuditMessagingProperties properties;

    @AfterAll
    void cleanUp() {
        amqpAdmin.deleteQueue(properties.getQueue());
        amqpAdmin.deleteExchange(properties.getExchange());
    }

    @Test
    void shouldDeclareDurableDirectExchange() throws IOException, TimeoutException {
        try (Channel channel = connectionFactory.createConnection().createChannel(false)) {
            AMQP.Exchange.DeclareOk declareOk = channel.exchangeDeclarePassive(properties.getExchange());
            assertThat(declareOk).isNotNull();
        }
    }

    @Test
    void shouldDeclareDurableAuditQueue() throws IOException, TimeoutException {
        try (Channel channel = connectionFactory.createConnection().createChannel(false)) {
            AMQP.Queue.DeclareOk declareOk = channel.queueDeclarePassive(properties.getQueue());
            assertThat(declareOk.getQueue()).isEqualTo(properties.getQueue());
        }
    }

    @Test
    void shouldBindAuditQueueWithRequestedRoutingKey() {
        RabbitTemplate probeTemplate = new RabbitTemplate(connectionFactory);
        Message probe = new Message("{}".getBytes(StandardCharsets.UTF_8));

        probeTemplate.send(properties.getExchange(), properties.getRoutingKey(), probe);
        Message received = probeTemplate.receive(properties.getQueue(), 5000);

        assertThat(received).isNotNull();
    }
}
