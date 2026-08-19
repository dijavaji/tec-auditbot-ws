package ec.com.technoloqie.auditbot.api.integration.rabbitmq;

import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.connection.CorrelationData.Confirm;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ec.com.technoloqie.auditbot.api.commons.exception.AuditPublicationException;
import ec.com.technoloqie.auditbot.api.commons.exception.InvalidAuditRequestException;
import ec.com.technoloqie.auditbot.api.dto.ConversationAuditRequestDto;
import ec.com.technoloqie.auditbot.api.service.ConversationAuditRequestValidator;
import ec.com.technoloqie.auditbot.api.service.IConversationAuditPublisher;
import lombok.extern.slf4j.Slf4j;

/**
 * Adaptador RabbitMQ del puerto {@link IConversationAuditPublisher}.
 * <p>
 * Valida la solicitud, la serializa exactamente una vez, comprueba el límite de
 * tamaño, publica un mensaje persistente con propiedades AMQP del contrato
 * {@code 1.0} y espera el publisher confirm correlacionado hasta el timeout
 * configurado. Cualquier NACK, mensaje devuelto, timeout, interrupción o fallo
 * técnico se traduce en {@link AuditPublicationException} sin exponer el payload.
 * <p>
 * No añade reintentos automáticos, endpoints HTTP, consumo ni análisis.
 */
@Component
@Slf4j
public final class RabbitMqConversationAuditPublisher implements IConversationAuditPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final ConversationAuditRequestValidator validator;
    private final AuditMessagingProperties properties;
    private final Clock clock;

    public RabbitMqConversationAuditPublisher(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            ConversationAuditRequestValidator validator,
            AuditMessagingProperties properties,
            Clock clock) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void publish(ConversationAuditRequestDto request) {
        validator.validate(request);

        byte[] body = serialize(request);
        checkPayloadSize(body);

        Message message = buildMessage(request, body);
        CorrelationData correlationData = new CorrelationData(request.eventId().toString());

        send(request, message, correlationData);
        awaitConfirmation(request, correlationData);

        log.info("Publicación de auditoría completada. resultado=OK eventId={} chatId={} exchange={} routingKey={}",
                request.eventId(), request.conversation().chatId(), properties.getExchange(),
                properties.getRoutingKey());
    }

    private byte[] serialize(ConversationAuditRequestDto request) {
        try {
            return objectMapper.writeValueAsBytes(request);
        } catch (JsonProcessingException e) {
            throw new AuditPublicationException(request.eventId(), "fallo de serialización del contrato", e);
        }
    }

    private void checkPayloadSize(byte[] body) {
        long maxBytes = properties.getMaxPayloadSize().toBytes();
        if (body.length > maxBytes) {
            throw new InvalidAuditRequestException(List.of("payload"));
        }
    }

    private Message buildMessage(ConversationAuditRequestDto request, byte[] body) {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType("application/json");
        messageProperties.setContentEncoding("UTF-8");
        messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        messageProperties.setMessageId(request.eventId().toString());
        messageProperties.setTimestamp(Date.from(clock.instant()));
        messageProperties.setHeader("schemaVersion", request.schemaVersion());
        messageProperties.setHeader("producer", request.producer());
        return new Message(body, messageProperties);
    }

    private void send(ConversationAuditRequestDto request, Message message, CorrelationData correlationData) {
        try {
            rabbitTemplate.send(properties.getExchange(), properties.getRoutingKey(), message, correlationData);
        } catch (RuntimeException e) {
            log.warn("Fallo al enviar la solicitud de auditoría a RabbitMQ. resultado=ERROR eventId={} chatId={} "
                    + "exchange={} routingKey={}", request.eventId(), request.conversation().chatId(),
                    properties.getExchange(), properties.getRoutingKey());
            throw new AuditPublicationException(request.eventId(), "fallo de envío al broker", e);
        }
    }

    private void awaitConfirmation(ConversationAuditRequestDto request, CorrelationData correlationData) {
        Confirm confirm;
        try {
            confirm = correlationData.getFuture()
                    .get(properties.getConfirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logFailure(request);
            throw new AuditPublicationException(request.eventId(), "hilo interrumpido esperando confirmación", e);
        } catch (ExecutionException e) {
            logFailure(request);
            throw new AuditPublicationException(request.eventId(), "fallo al obtener confirmación del broker",
                    e.getCause() != null ? e.getCause() : e);
        } catch (TimeoutException e) {
            logFailure(request);
            throw new AuditPublicationException(request.eventId(), "timeout esperando confirmación del broker", e);
        }

        if (confirm == null || !confirm.ack()) {
            logFailure(request);
            throw new AuditPublicationException(request.eventId(), "confirmación negativa del broker");
        }

        if (correlationData.getReturned() != null) {
            logFailure(request);
            throw new AuditPublicationException(request.eventId(), "mensaje devuelto por no ser enrutable");
        }
    }

    private void logFailure(ConversationAuditRequestDto request) {
        log.warn("Publicación de auditoría fallida. resultado=ERROR eventId={} chatId={} exchange={} routingKey={}",
                request.eventId(), request.conversation().chatId(), properties.getExchange(),
                properties.getRoutingKey());
    }
}
