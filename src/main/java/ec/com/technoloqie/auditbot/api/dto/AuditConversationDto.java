package ec.com.technoloqie.auditbot.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * Representa la conversación completa a auditar dentro del contrato
 * {@code conversation.audit.requested} versión {@code 1.0}.
 * <p>
 * La lista {@code messages} recibe copia defensiva mediante {@link List#copyOf(java.util.Collection)}
 * cuando no es nula, para garantizar inmutabilidad. La responsabilidad de reportar listas
 * nulas o vacías permanece en el validador, por lo que aquí no se sustituye {@code null} por
 * una lista vacía.
 *
 * @param chatId      identificador de la conversación auditada
 * @param assistantId identificador del asistente de IA que participó en la conversación
 * @param startedAt   instante de inicio de la conversación
 * @param endedAt      instante de fin de la conversación, puede ser nulo si aún no ha finalizado
 * @param messages    lista ordenada cronológicamente de mensajes de la conversación
 */
public record AuditConversationDto(
        String chatId,
        String assistantId,
        Instant startedAt,
        Instant endedAt,
        List<AuditMessageDto> messages) {

    public AuditConversationDto(String chatId, String assistantId, Instant startedAt, Instant endedAt,
            List<AuditMessageDto> messages) {
        this.chatId = chatId;
        this.assistantId = assistantId;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.messages = messages == null ? null : List.copyOf(messages);
    }
}
