package ec.com.technoloqie.auditbot.api.dto;

/**
 * Representa un turno de diálogo entre el usuario y el asistente en una conversación auditada.
 *
 * @param userMessageId      identificador del mensaje del usuario
 * @param userQuestion       texto de la consulta realizada por el usuario
 * @param assistantMessageId identificador del mensaje de respuesta del asistente (puede ser nulo si no hubo respuesta)
 * @param assistantResponse  texto de la respuesta emitida por el asistente (puede ser nulo o vacío)
 */
public record ConversationTurnDto(
        String userMessageId,
        String userQuestion,
        String assistantMessageId,
        String assistantResponse
) {
}
