package ec.com.technoloqie.auditbot.api.integration.rabbitmq;

import java.time.Clock;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import ec.com.technoloqie.auditbot.api.service.ConversationAuditRequestValidator;

/**
 * Declara la topología durable de RabbitMQ para {@code US-01} (exchange, cola y
 * binding) y los beans auxiliares requeridos por el adaptador de publicación.
 * <p>
 * Spring Boot provee automáticamente {@code RabbitTemplate}, {@code ConnectionFactory},
 * {@code RabbitAdmin} y {@code ObjectMapper}; esta clase no los redefine.
 */
@Configuration
@EnableConfigurationProperties(AuditMessagingProperties.class)
public class RabbitMqConfiguration {

    @Bean
    public DirectExchange auditExchange(AuditMessagingProperties properties) {
        properties.validate();
        return new DirectExchange(properties.getExchange(), true, false);
    }

    @Bean
    public Queue auditQueue(AuditMessagingProperties properties) {
        properties.validate();
        return new Queue(properties.getQueue(), true, false, false);
    }

    @Bean
    public Binding auditRequestBinding(Queue auditQueue, DirectExchange auditExchange,
            AuditMessagingProperties properties) {
        return BindingBuilder.bind(auditQueue).to(auditExchange).with(properties.getRoutingKey());
    }

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
