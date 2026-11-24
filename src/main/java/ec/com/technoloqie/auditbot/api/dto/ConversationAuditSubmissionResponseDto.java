package ec.com.technoloqie.auditbot.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Respuesta devuelta al cliente tras admitir exitosamente una conversación para auditoría asíncrona.
 *
 * @param eventId     identificador único del evento publicado en RabbitMQ
 * @param chatId      identificador de la conversación auditada
 * @param status      estado de la admisión (ej. "ACCEPTED")
 * @param message     mensaje descriptivo de confirmación
 * @param submittedAt instante en el que fue admitida la solicitud
 */
public record ConversationAuditSubmissionResponseDto(
        UUID eventId,
        String chatId,
        String status,
        String message,
        Instant submittedAt) {
}
