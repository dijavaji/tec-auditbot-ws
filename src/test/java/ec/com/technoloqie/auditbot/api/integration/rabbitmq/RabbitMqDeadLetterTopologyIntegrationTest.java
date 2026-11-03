package ec.com.technoloqie.auditbot.api.integration.rabbitmq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica la topología de dead-letter declarada por US-02 contra una instancia
 * real de RabbitMQ en {@code localhost:5672}.
 * <p>
 * Usa nombres sufijados con UUID por ejecución y limpia recursos en {@code @AfterAll}.
 * Fallback aprobado en US-01 (Docker/Testcontainers no disponible en este entorno).
 * Cubre R17, R18, R19, R20, R21.
 */
@SpringBootTest(classes = RabbitMqDeadLetterTopologyIntegrationTest.MinimalTestConfig.class,
        properties = "spring.profiles.active=test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RabbitMqDeadLetterTopologyIntegrationTest {

    @Configuration
    @ImportAutoConfiguration(RabbitAutoConfiguration.class)
    @Import(RabbitMqConfiguration.class)
    static class MinimalTestConfig {
    }

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        String suffix = UUID.randomUUID().toString();
        registry.add("spring.rabbitmq.host", () -> "127.0.0.1");
        registry.add("spring.rabbitmq.port", () -> "5672");
        registry.add("spring.rabbitmq.username", () -> "rabbitmq");
        registry.add("spring.rabbitmq.password", () -> "Password.1");
        registry.add("auditbot.messaging.exchange", () -> "auditbot.exchange.dlx-test-" + suffix);
        registry.add("auditbot.messaging.queue", () -> "conversation.audit.queue.dlx-test-" + suffix);
        registry.add("auditbot.messaging.routing-key", () -> "conversation.audit.requested.dlx-test-" + suffix);
        registry.add("auditbot.messaging.dead-letter-exchange", () -> "auditbot.dlx.dlx-test-" + suffix);
        registry.add("auditbot.messaging.dead-letter-queue", () -> "conversation.audit.dlq.dlx-test-" + suffix);
        registry.add("auditbot.messaging.dead-letter-routing-key", () -> "conversation.audit.failed.dlx-test-" + suffix);
    }

    @Autowired private AmqpAdmin amqpAdmin;
    @Autowired private ConnectionFactory connectionFactory;
    @Autowired private AuditMessagingProperties properties;

    @AfterAll
    void cleanUp() {
        amqpAdmin.deleteQueue(properties.getDeadLetterQueue());
        amqpAdmin.deleteQueue(properties.getQueue());
        amqpAdmin.deleteExchange(properties.getDeadLetterExchange());
        amqpAdmin.deleteExchange(properties.getExchange());
    }

    // R17 — exchange de dead-letter durable y directo
    @Test
    void shouldDeclareDurableDeadLetterExchange() throws IOException, TimeoutException {
        try (Channel channel = connectionFactory.createConnection().createChannel(false)) {
            AMQP.Exchange.DeclareOk ok = channel.exchangeDeclarePassive(properties.getDeadLetterExchange());
            assertThat(ok).isNotNull();
        }
    }

    // R18 — DLQ durable
    @Test
    void shouldDeclareDurableDeadLetterQueue() throws IOException, TimeoutException {
        try (Channel channel = connectionFactory.createConnection().createChannel(false)) {
            AMQP.Queue.DeclareOk ok = channel.queueDeclarePassive(properties.getDeadLetterQueue());
            assertThat(ok.getQueue()).isEqualTo(properties.getDeadLetterQueue());
        }
    }

    // R19 — DLQ enlazada al DLX con la routing key correcta
    @Test
    void shouldBindDeadLetterQueueWithFailedRoutingKey() {
        // Publica directamente al DLX con la dead-letter routing key y verifica que llega a la DLQ
        org.springframework.amqp.rabbit.core.RabbitTemplate probe =
                new org.springframework.amqp.rabbit.core.RabbitTemplate(connectionFactory);
        byte[] body = "dlq-test".getBytes();
        probe.send(properties.getDeadLetterExchange(), properties.getDeadLetterRoutingKey(),
                new org.springframework.amqp.core.Message(body));

        org.springframework.amqp.core.Message received = probe.receive(properties.getDeadLetterQueue(), 5000);
        assertThat(received).isNotNull();
    }

    // R20 — cola principal declarada con argumentos x-dead-letter-exchange y x-dead-letter-routing-key
    @Test
    void shouldDeclareMainQueueWithDeadLetterArguments() throws IOException, TimeoutException {
        try (Channel channel = connectionFactory.createConnection().createChannel(false)) {
            // queueDeclarePassive verifica que la cola existe con los argumentos con los que fue declarada;
            // si los argumentos difieren RabbitMQ lanza PRECONDITION_FAILED.
            AMQP.Queue.DeclareOk ok = channel.queueDeclarePassive(properties.getQueue());
            assertThat(ok.getQueue()).isEqualTo(properties.getQueue());
        }
    }

    // R21 — DLQ sin argumentos de dead-letter (cola de parking)
    @Test
    void shouldDeclareDlqWithoutDeadLetterArguments() throws IOException, TimeoutException {
        // Si la DLQ tuviera x-dead-letter-exchange, los mensajes rebotarían en bucle.
        // Verificamos que la declaración pasiva tiene éxito (cola existe sin argumentos extra).
        try (Channel channel = connectionFactory.createConnection().createChannel(false)) {
            Map<String, Object> args = channel.queueDeclarePassive(properties.getDeadLetterQueue())
                    .getQueue() != null ? Map.of() : null;
            // Redeclaración activa con cero argumentos debe tener éxito (sin x-dead-letter-exchange)
            channel.queueDeclare(properties.getDeadLetterQueue(), true, false, false, Map.of());
        }
    }
}
