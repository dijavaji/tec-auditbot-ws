package ec.com.technoloqie.auditbot.api.model;

/**
 * Estados posibles de un registro de auditoría.
 * <p>
 * US-02 escribe únicamente {@code PENDING} → {@code PROCESSING} → {@code FAILED}.
 * {@code COMPLETED} queda declarado en el catálogo para US-06 y US-08; US-02 nunca lo establece.
 */
public enum AuditStatus {
    /** Admisión registrada, pipeline aún no iniciado. */
    PENDING,
    /** Pipeline de análisis en curso. */
    PROCESSING,
    /** Pipeline completado con éxito. Escribe US-06. */
    COMPLETED,
    /** El mensaje terminó en la DLQ o el pipeline falló sin posibilidad de reintento. */
    FAILED
}
