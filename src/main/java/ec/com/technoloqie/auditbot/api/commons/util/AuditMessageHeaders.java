package ec.com.technoloqie.auditbot.api.commons.util;

/**
 * Constantes de los headers AMQP propios de la topología de auditoría.
 * <p>
 * Centraliza los nombres de headers para evitar literales dispersos en el código
 * y facilitar la búsqueda de usos.
 */
public final class AuditMessageHeaders {

    /** Header que indica que el consumidor debe reprocesar un {@code chatId} ya procesado. */
    public static final String X_AUDIT_RETRY = "x-audit-retry";

    /** Razón técnica corta del descarte hacia la DLQ. */
    public static final String X_AUDIT_FAILURE_REASON = "x-audit-failure-reason";

    /** Rutas de campos inválidos separadas por coma; presente solo cuando el validador las reporta. */
    public static final String X_AUDIT_INVALID_FIELDS = "x-audit-invalid-fields";

    /** Instante ISO-8601 UTC en que el mensaje fue descartado hacia la DLQ. */
    public static final String X_AUDIT_FAILED_AT = "x-audit-failed-at";

    /** Header nativo de RabbitMQ con el historial de redirecciones de dead-letter. */
    public static final String X_DEATH = "x-death";

    private AuditMessageHeaders() {
        // clase de constantes, no instanciable
    }
}
