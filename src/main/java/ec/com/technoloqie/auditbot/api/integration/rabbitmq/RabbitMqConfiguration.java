package ec.com.technoloqie.auditbot.api.integration.rabbitmq;

import java.time.Clock;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import ec.com.technoloqie.auditbot.api.service.ConversationAuditRequestValidator;

/**
 * Declara la topología durable de RabbitMQ y los beans auxiliares requeridos por los
 * adaptadores de publicación y consumo.
 * <p>
 * US-01: exchange principal, cola principal y binding de publicación.<br>
 * US-02: redeclaración de la cola principal con argumentos de dead-letter,
 * exchange de dead-letter ({@code auditbot.dlx}), cola de parking
 * ({@code conversation.audit.dlq}) y binding de dead-letter.
 * <p>
 * Spring Boot provee automáticamente {@code RabbitTemplate}, {@code ConnectionFactory},
 * {@code RabbitAdmin} y {@code ObjectMapper}; esta clase no los redefine.
 */
@Configuration
@EnableConfigurationProperties(AuditMessagingProperties.class)
public class RabbitMqConfiguration {

    // -------------------------------------------------------------------------
    // US-01 — topología principal
    // -------------------------------------------------------------------------

    @Bean
    public DirectExchange auditExchange(AuditMessagingProperties properties) {
        properties.validate();
        return new DirectExchange(properties.getExchange(), true, false);
    }

    /**
     * Cola principal redeclarada con argumentos de dead-letter (US-02).
     * Durable, no exclusiva, no auto-delete.
     */
    @Bean
    public Queue auditQueue(AuditMessagingProperties properties) {
        properties.validate();
        return QueueBuilder.durable(properties.getQueue())
                .withArgument("x-dead-letter-exchange", properties.getDeadLetterExchange())
                .withArgument("x-dead-letter-routing-key", properties.getDeadLetterRoutingKey())
                .build();
    }

    @Bean
    public Binding auditRequestBinding(Queue auditQueue, DirectExchange auditExchange,
            AuditMessagingProperties properties) {
        return BindingBuilder.bind(auditQueue).to(auditExchange).with(properties.getRoutingKey());
    }

    // -------------------------------------------------------------------------
    // US-02 — topología de dead-letter
    // -------------------------------------------------------------------------

    /**
     * Exchange de dead-letter. Directo, durable, no auto-delete.
     */
    @Bean
    public DirectExchange auditDeadLetterExchange(AuditMessagingProperties properties) {
        return new DirectExchange(properties.getDeadLetterExchange(), true, false);
    }

    /**
     * Cola de parking de mensajes inválidos o con reintentos agotados.
     * Durable, no exclusiva, no auto-delete, <strong>sin</strong> argumentos de dead-letter
     * (no hay rebote desde la DLQ).
     */
    @Bean
    public Queue auditDeadLetterQueue(AuditMessagingProperties properties) {
        return QueueBuilder.durable(properties.getDeadLetterQueue()).build();
    }

    @Bean
    public Binding auditDeadLetterBinding(Queue auditDeadLetterQueue,
            DirectExchange auditDeadLetterExchange, AuditMessagingProperties properties) {
        return BindingBuilder.bind(auditDeadLetterQueue)
                .to(auditDeadLetterExchange)
                .with(properties.getDeadLetterRoutingKey());
    }

    // -------------------------------------------------------------------------
    // Beans auxiliares compartidos
    // -------------------------------------------------------------------------

    @Bean
    public ConversationAuditRequestValidator conversationAuditRequestValidator() {
        return new ConversationAuditRequestValidator();
    }

    @Bean
    public Clock auditPublicationClock() {
        return Clock.systemUTC();
    }

    /**
     * Provee un {@link ObjectMapper} de Jackson 2 con soporte para {@code java.time}.
     * <p>
     * Spring Boot 4 auto-configura por defecto un {@code ObjectMapper} de Jackson 3
     * ({@code tools.jackson.databind.ObjectMapper}), que no es compatible con el
     * {@code com.fasterxml.jackson.databind.ObjectMapper} usado por el contrato de
     * este servicio. Este bean solo se declara si no existe ya uno gestionado.
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper auditObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}
