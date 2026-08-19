package ec.com.technoloqie.auditbot.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Contrato inmutable del evento {@code conversation.audit.requested} versión {@code 1.0}.
 * <p>
 * Representa la solicitud de auditoría de una conversación completa. Este DTO es la
 * frontera pública del puerto {@link ec.com.technoloqie.auditbot.api.service.IConversationAuditPublisher}
 * y no depende de Spring AMQP ni del cliente de RabbitMQ.
 *
 * @param eventId       identificador único del evento, usado como {@code messageId} AMQP y
 *                      correlación de publisher confirm
 * @param schemaVersion versión del contrato, debe ser exactamente {@code "1.0"}
 * @param occurredAt    instante lógico de creación del evento
 * @param producer      identificador del sistema productor del evento
 * @param conversation  conversación completa a auditar
 */
public record ConversationAuditRequestDto(
        UUID eventId,
        String schemaVersion,
        Instant occurredAt,
        String producer,
        AuditConversationDto conversation) {
}
