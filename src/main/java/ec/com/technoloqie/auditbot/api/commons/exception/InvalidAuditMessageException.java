package ec.com.technoloqie.auditbot.api.commons.exception;

import java.util.Collections;
import java.util.List;

/**
 * Error permanente en el consumo de un mensaje de auditoría.
 * <p>
 * Indica que el mismo mensaje nunca será válido (JSON malformado, esquema inválido,
 * payload sobredimensionado, {@code schemaVersion} no soportada). El listener lo
 * traduce en {@code AmqpRejectAndDontRequeueException} para enrutar el mensaje a la
 * DLQ sin reintentos.
 * <p>
 * No almacena el cuerpo JSON completo ni {@code messages[].content}.
 */
public final class InvalidAuditMessageException extends RuntimeException {

    private final String reason;
    private final List<String> invalidFields;

    public InvalidAuditMessageException(String reason) {
        super(reason);
        this.reason = reason;
        this.invalidFields = Collections.emptyList();
    }

    public InvalidAuditMessageException(String reason, List<String> invalidFields, Throwable cause) {
        super(reason, cause);
        this.reason = reason;
        this.invalidFields = invalidFields != null ? List.copyOf(invalidFields) : Collections.emptyList();
    }

    public String getReason() {
        return reason;
    }

    public List<String> getInvalidFields() {
        return invalidFields;
    }
}
