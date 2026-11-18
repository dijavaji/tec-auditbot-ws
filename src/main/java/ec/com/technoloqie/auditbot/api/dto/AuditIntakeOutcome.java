package ec.com.technoloqie.auditbot.api.dto;

/**
 * Posibles desenlaces del intento de admisión de un mensaje de auditoría.
 */
public enum AuditIntakeOutcome {
    /** Conversación admitida por primera vez; registro en {@code PROCESSING}. */
    ADMITTED,
    /** {@code chatId} ya procesado y sin reintento explícito; mensaje descartado. */
    DUPLICATE_SKIPPED,
    /** Reintento explícito admitido; registro vuelto a {@code PROCESSING}. */
    RETRY_ADMITTED
}
