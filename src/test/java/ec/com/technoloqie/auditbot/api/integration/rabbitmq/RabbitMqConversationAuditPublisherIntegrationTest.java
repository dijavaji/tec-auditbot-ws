package ec.com.technoloqie.auditbot.api.integration.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;
import ec.com.technoloqie.auditbot.api.dto.AuditMessageDto;
import ec.com.technoloqie.auditbot.api.dto.ConversationAuditRequestDto;
import ec.com.technoloqie.auditbot.api.service.IConversationAuditPublisher;

/**
 * Verifica de extremo a extremo la publicación, routing y persistencia de
 * {@code US-01} contra una instancia real de RabbitMQ (el mismo broker
 * externo usado en {@link RabbitMqTopologyIntegrationTest}, dado que
 * Testcontainers/Docker no está disponible en este entorno).
 * <p>
 * Cada método usa nombres de exchange/cola/routing-key únicos para
 * aislarse y limpia sus recursos al finalizar la clase. Cubre R1, R3, R8,
 * R16, R19.
 */
@SpringBootTest(classes = RabbitMqConversationAuditPublisherIntegrationTest.MinimalTestConfig.class,
        properties = "spring.profiles.active=test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RabbitMqConversationAuditPublisherIntegrationTest {

    @Configuration
    @ImportAutoConfiguration(RabbitAutoConfiguration.class)
    @Import({RabbitMqConfiguration.class, RabbitMqConversationAuditPublisher.class})
    static class MinimalTestConfig {

        @org.springframework.context.annotation.Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        }
    }

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", () -> "127.0.0.1");
        registry.add("spring.rabbitmq.port", () -> "5672");
        registry.add("spring.rabbitmq.username", () -> "rabbitmq");
        registry.add("spring.rabbitmq.password", () -> "Password.1");
        registry.add("spring.rabbitmq.publisher-confirm-type", () -> "correlated");
        registry.add("spring.rabbitmq.publisher-returns", () -> "true");
        registry.add("spring.rabbitmq.template.mandatory", () -> "true");
        String suffix = UUID.randomUUID().toString();
        registry.add("auditbot.messaging.exchange", () -> "auditbot.exchange.publisher-test-" + suffix);
        registry.add("auditbot.messaging.queue", () -> "conversation.audit.queue.publisher-test-" + suffix);
        registry.add("auditbot.messaging.routing-key",
                () -> "conversation.audit.requested.publisher-test-" + suffix);
    }

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private ConnectionFactory connectionFactory;

    @Autowired
    private AuditMessagingProperties properties;

    @Autowired
    private IConversationAuditPublisher publisher;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterAll
    void cleanUp() {
        amqpAdmin.deleteQueue(properties.getQueue());
        amqpAdmin.deleteExchange(properties.getExchange());
    }

    private ConversationAuditRequestDto validRequest() {
        Instant now = Instant.parse("2026-08-31T09:00:00Z");
        AuditMessageDto message = new AuditMessageDto("msg-1", "USER", "hola, necesito ayuda", now);
        AuditConversationDto conversation = new AuditConversationDto("chat-integration-1", "assistant-1", now,
                now.plusSeconds(30), List.of(message));
        return new ConversationAuditRequestDto(UUID.randomUUID(), "1.0", now, "smart-chatbot", conversation);
    }

    @Test
    void shouldRouteValidRequestToAuditQueue() {
        ConversationAuditRequestDto request = validRequest();

        publisher.publish(request);

        RabbitTemplate probe = new RabbitTemplate(connectionFactory);
        Message received = probe.receive(properties.getQueue(), 5000);

        assertThat(received).isNotNull();
    }

    @Test
    void shouldPublishVersionOneHeadersAndBody() throws Exception {
        ConversationAuditRequestDto request = validRequest();

        publisher.publish(request);

        RabbitTemplate probe = new RabbitTemplate(connectionFactory);
        Message received = probe.receive(properties.getQueue(), 5000);

        assertThat(received).isNotNull();
        assertThat(received.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(received.getMessageProperties().getHeaders().get("schemaVersion")).isEqualTo("1.0");
        assertThat(received.getMessageProperties().getHeaders().get("producer")).isEqualTo("smart-chatbot");

        ConversationAuditRequestDto deserialized = objectMapper.readValue(received.getBody(),
                ConversationAuditRequestDto.class);
        assertThat(deserialized.eventId()).isEqualTo(request.eventId());
        assertThat(deserialized.conversation().chatId()).isEqualTo(request.conversation().chatId());
    }

    @Test
    void shouldKeepPersistentMessageUntilConsumed() {
        ConversationAuditRequestDto request = validRequest();

        publisher.publish(request);

        RabbitTemplate probe = new RabbitTemplate(connectionFactory);
        Message received = probe.receive(properties.getQueue(), 5000);

        assertThat(received).isNotNull();
        assertThat(received.getMessageProperties().getReceivedDeliveryMode().name()).isEqualTo("PERSISTENT");
    }

    @Test
    void shouldFailWhenMandatoryMessageIsUnroutable() {
        String routingKeyWithoutBinding = "route.without.binding." + UUID.randomUUID();

        RabbitTemplate directTemplate = new RabbitTemplate(connectionFactory);
        directTemplate.setMandatory(true);

        org.springframework.amqp.rabbit.connection.CorrelationData correlationData =
                new org.springframework.amqp.rabbit.connection.CorrelationData(UUID.randomUUID().toString());
        Message message = new Message("{}".getBytes(StandardCharsets.UTF_8));

        directTemplate.send(properties.getExchange(), routingKeyWithoutBinding, message, correlationData);

        org.awaitility.Awaitility.await()
                .atMost(java.time.Duration.ofSeconds(5))
                .until(() -> correlationData.getReturned() != null);

        assertThat(correlationData.getReturned()).isNotNull();
    }
}
