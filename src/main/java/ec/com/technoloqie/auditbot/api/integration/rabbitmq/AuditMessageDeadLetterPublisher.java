package ec.com.technoloqie.auditbot.api.integration.rabbitmq;

import ec.com.technoloqie.auditbot.api.commons.util.AuditMessageHeaders;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Publica mensajes rechazados en la DLQ con headers de diagnóstico.
 * <p>
 * Encamina al exchange {@code auditbot.dlx} con routing key {@code conversation.audit.failed}.
 * Los headers adjuntos ({@code x-audit-failure-reason}, {@code x-audit-invalid-fields},
 * {@code x-audit-failed-at}) no contienen el cuerpo completo ni {@code messages[].content}.
 */
@Component
public class AuditMessageDeadLetterPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final AuditMessagingProperties properties;
    private final Clock clock;

    public AuditMessageDeadLetterPublisher(RabbitTemplate rabbitTemplate,
            AuditMessagingProperties properties, Clock clock) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Publica el mensaje original hacia la DLQ añadiendo headers de diagnóstico.
     *
     * @param original      mensaje AMQP original recibido por el listener
     * @param failureReason razón técnica corta del descarte (ej. {@code MALFORMED_JSON})
     * @param invalidFields rutas de campos inválidos; puede ser nula o vacía
     */
    public void publish(Message original, String failureReason, List<String> invalidFields) {
        String failedAt = Instant.now(clock).toString();

        Message dlqMessage = MessageBuilder.fromMessage(original)
                .setHeader(AuditMessageHeaders.X_AUDIT_FAILURE_REASON, failureReason)
                .setHeader(AuditMessageHeaders.X_AUDIT_FAILED_AT, failedAt)
                .build();

        if (invalidFields != null && !invalidFields.isEmpty()) {
            dlqMessage.getMessageProperties().setHeader(
                    AuditMessageHeaders.X_AUDIT_INVALID_FIELDS,
                    String.join(",", invalidFields));
        }

        rabbitTemplate.send(
                properties.getDeadLetterExchange(),
                properties.getDeadLetterRoutingKey(),
                dlqMessage);
    }
}
