package ec.com.technoloqie.auditbot.api.integration.rabbitmq;

import ec.com.technoloqie.auditbot.api.commons.exception.AuditProcessingException;
import ec.com.technoloqie.auditbot.api.commons.exception.InvalidAuditMessageException;
import ec.com.technoloqie.auditbot.api.commons.util.AuditMessageHeaders;
import ec.com.technoloqie.auditbot.api.dto.AuditIntakeOutcome;
import ec.com.technoloqie.auditbot.api.dto.AuditIntakeResultDto;
import ec.com.technoloqie.auditbot.api.dto.ConversationAuditRequestDto;
import ec.com.technoloqie.auditbot.api.service.IConversationAuditIntakeService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Adaptador AMQP delgado para el consumo de solicitudes de auditoría.
 * <p>
 * Responsabilidades exclusivas:
 * <ul>
 *   <li>Gestión del MDC ({@code chatId}, {@code eventId}).</li>
 *   <li>Guardia de tamaño de payload.</li>
 *   <li>Guardia de redelivery por {@code x-death}.</li>
 *   <li>Deserialización con {@link AuditMessageDeserializer}.</li>
 *   <li>Delegación en {@link IConversationAuditIntakeService}.</li>
 *   <li>Clasificación de excepciones y enrutamiento a DLQ.</li>
 *   <li>Log estructurado de resultado (una línea por mensaje).</li>
 * </ul>
 * Sin reglas de negocio, sin acceso a repositorios, sin {@code @Transactional}.
 */
@Slf4j
@Component
public class ConversationAuditMessageListener {

    private static final String MDC_CHAT_ID = "chatId";
    private static final String MDC_EVENT_ID = "eventId";

    private final AuditMessageDeserializer deserializer;
    private final IConversationAuditIntakeService intakeService;
    private final AuditMessageDeadLetterPublisher deadLetterPublisher;
    private final AuditMessagingProperties properties;

    public ConversationAuditMessageListener(
            AuditMessageDeserializer deserializer,
            IConversationAuditIntakeService intakeService,
            AuditMessageDeadLetterPublisher deadLetterPublisher,
            AuditMessagingProperties properties) {
        this.deserializer = deserializer;
        this.intakeService = intakeService;
        this.deadLetterPublisher = deadLetterPublisher;
        this.properties = properties;
    }

    /**
     * Punto de entrada del consumo. El nombre de la cola se resuelve desde el bean
     * {@code auditQueue} para evitar literales en código (R2).
     */
    @RabbitListener(id = "conversationAuditListener", queues = "#{auditQueue.name}")
    public void onMessage(Message message) {
        String rawMessageId = message.getMessageProperties().getMessageId();
        String queue = message.getMessageProperties().getConsumerQueue();

        try {
            processMessage(message, queue, rawMessageId);
        } finally {
            MDC.remove(MDC_CHAT_ID);
            MDC.remove(MDC_EVENT_ID);
        }
    }

    private void processMessage(Message message, String queue, String rawMessageId) {
        byte[] body = message.getBody();

        // 1. Guardia de tamaño — antes de deserializar
        long maxBytes = properties.getMaxPayloadSize().toBytes();
        if (body != null && body.length > maxBytes) {
            String reason = "OVERSIZED_PAYLOAD";
            deadLetterPublisher.publish(message, reason, null);
            logResult("DESCARTADO_OVERSIZED", null, rawMessageId, null, queue, 1, null);
            throw new AmqpRejectAndDontRequeueException("Payload sobredimensionado: " + body.length + " bytes");
        }

        // 2. Guardia de redelivery por x-death
        int deathCount = extractDeathCount(message);
        if (Boolean.TRUE.equals(message.getMessageProperties().isRedelivered()) && deathCount >= properties.getMaxRedeliveries()) {
            String reason = "REDELIVERY_LIMIT_REACHED";
            deadLetterPublisher.publish(message, reason, null);
            logResult("DESCARTADO_REDELIVERY", null, rawMessageId, null, queue, deathCount, null);
            throw new AmqpRejectAndDontRequeueException("Límite de redelivery alcanzado: " + deathCount);
        }

        // 3. Deserializar
        ConversationAuditRequestDto request;
        try {
            request = deserializer.deserialize(body);
        } catch (InvalidAuditMessageException ex) {
            deadLetterPublisher.publish(message, ex.getReason(), ex.getInvalidFields());
            logResult("DESCARTADO_MALFORMED", null, rawMessageId, null, queue, deathCount + 1, null);
            throw new AmqpRejectAndDontRequeueException("JSON malformado", ex);
        }

        // Enriquecer MDC una vez que tenemos chatId y eventId
        String chatId = request.conversation() != null ? request.conversation().chatId() : null;
        String eventId = request.eventId() != null ? request.eventId().toString() : null;
        String assistantId = request.conversation() != null ? request.conversation().assistantId() : null;
        MDC.put(MDC_CHAT_ID, chatId != null ? chatId : "unknown");
        MDC.put(MDC_EVENT_ID, eventId != null ? eventId : "unknown");

        // 4. Leer header de reintento
        boolean retryRequested = Boolean.TRUE.equals(
                message.getMessageProperties().getHeaders().get(AuditMessageHeaders.X_AUDIT_RETRY));

        // 5. Delegar admisión
        try {
            AuditIntakeResultDto result = intakeService.admit(request, retryRequested);
            logOutcome(result, rawMessageId, queue, deathCount + 1, assistantId);
        } catch (InvalidAuditMessageException ex) {
            // Error permanente del servicio (esquema inválido post-validación)
            deadLetterPublisher.publish(message, ex.getReason(), ex.getInvalidFields());
            log.warn("resultado=DESCARTADO_INVALIDO chatId={} eventId={} messageId={} queue={} intento={} assistantId={}",
                    chatId, eventId, rawMessageId, queue, deathCount + 1, assistantId);
            throw new AmqpRejectAndDontRequeueException("Mensaje inválido: " + ex.getReason(), ex);
        } catch (AuditProcessingException ex) {
            // Error transitorio: propagar para activar el retry acotado del contenedor
            log.error("resultado=ERROR_TRANSITORIO chatId={} eventId={} messageId={} queue={} intento={} assistantId={}",
                    chatId, eventId, rawMessageId, queue, deathCount + 1, assistantId);
            throw ex;
        }
    }

    private void logOutcome(AuditIntakeResultDto result, String messageId, String queue,
            int intento, String assistantId) {
        String chatId = result.chatId();

        if (result.outcome() == AuditIntakeOutcome.ADMITTED || result.outcome() == AuditIntakeOutcome.RETRY_ADMITTED) {
            log.info("resultado={} chatId={} eventId={} messageId={} queue={} intento={} assistantId={}",
                    result.outcome(), chatId, MDC.get(MDC_EVENT_ID), messageId, queue, intento, assistantId);
        } else {
            log.warn("resultado={} chatId={} eventId={} messageId={} queue={} intento={} assistantId={}",
                    result.outcome(), chatId, MDC.get(MDC_EVENT_ID), messageId, queue, intento, assistantId);
        }
    }

    private void logResult(String resultado, String chatId, String messageId, String eventId,
            String queue, int intento, String assistantId) {
        log.warn("resultado={} chatId={} eventId={} messageId={} queue={} intento={} assistantId={}",
                resultado, chatId, eventId, messageId, queue, intento, assistantId);
    }

    @SuppressWarnings("unchecked")
    private int extractDeathCount(Message message) {
        Object xDeath = message.getMessageProperties().getHeaders().get(AuditMessageHeaders.X_DEATH);
        if (!(xDeath instanceof List)) {
            return 0;
        }
        List<?> deathList = (List<?>) xDeath;
        if (deathList.isEmpty()) {
            return 0;
        }
        Object first = deathList.get(0);
        if (!(first instanceof Map)) {
            return 0;
        }
        Map<?, ?> deathMap = (Map<?, ?>) first;
        Object count = deathMap.get("count");
        if (count instanceof Number) {
            return ((Number) count).intValue();
        }
        return 0;
    }
}
