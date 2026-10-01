package ec.com.technoloqie.auditbot.api.commons.exception;

import java.util.UUID;

/**
 * Excepción lanzada cuando la publicación de una {@code ConversationAuditRequestDto}
 * hacia RabbitMQ falla por cualquier motivo: NACK, timeout, mensaje devuelto, fallo
 * de serialización o fallo de envío.
 * <p>
 * El mensaje contiene únicamente la razón técnica y el {@code eventId}; nunca el
 * payload de la conversación ni {@code messages[].content}.
 */
public final class AuditPublicationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final UUID eventId;

    public AuditPublicationException(UUID eventId, String reason) {
        super(buildMessage(eventId, reason));
        this.eventId = eventId;
    }

    public AuditPublicationException(UUID eventId, String reason, Throwable cause) {
        super(buildMessage(eventId, reason), cause);
        this.eventId = eventId;
    }

    private static String buildMessage(UUID eventId, String reason) {
        return "Fallo al publicar la solicitud de auditoría [eventId=" + eventId + "]: " + reason;
    }

    public UUID getEventId() {
        return eventId;
    }
}
