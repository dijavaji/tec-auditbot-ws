package ec.com.technoloqie.auditbot.api.dto;

/**
 * Resultado de la admisión de un mensaje de auditoría.
 *
 * @param outcome  desenlace de la admisión
 * @param chatId   identificador de la conversación
 * @param auditId  identificador del registro de auditoría creado o existente; puede ser nulo
 *                 cuando el desenlace es {@code DUPLICATE_SKIPPED} y no hay registro previo accesible
 */
public record AuditIntakeResultDto(AuditIntakeOutcome outcome, String chatId, String auditId) {
}
