package ec.com.technoloqie.auditbot.api.dto;

import java.time.Instant;

/**
 * Representa un único mensaje dentro de una conversación auditada.
 * <p>
 * Forma parte del contrato {@code conversation.audit.requested} versión {@code 1.0}.
 * {@code role} se mantiene como {@code String} en la frontera para que el validador
 * pueda rechazar explícitamente valores distintos de {@code USER}, {@code ASSISTANT}
 * y {@code SYSTEM} sin depender de un enum en la deserialización.
 *
 * @param messageId  identificador único del mensaje dentro de la conversación
 * @param role       rol del emisor del mensaje ({@code USER}, {@code ASSISTANT} o {@code SYSTEM})
 * @param content    contenido textual del mensaje
 * @param occurredAt instante en que ocurrió el mensaje
 */
public record AuditMessageDto(
        String messageId,
        String role,
        String content,
        Instant occurredAt) {
}
