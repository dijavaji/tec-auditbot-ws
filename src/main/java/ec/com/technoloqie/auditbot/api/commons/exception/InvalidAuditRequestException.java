package ec.com.technoloqie.auditbot.api.commons.exception;

import java.util.List;

/**
 * Excepción lanzada cuando una {@code ConversationAuditRequestDto} no cumple el contrato
 * o las reglas de validación de {@code US-01}.
 * <p>
 * Solo almacena las rutas de los campos inválidos, nunca valores de contenido ni el
 * payload completo, para evitar fugas de información sensible en logs o mensajes de error.
 */
public final class InvalidAuditRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<String> invalidFields;

    public InvalidAuditRequestException(List<String> invalidFields) {
        super("Solicitud de auditoría inválida. Campos inválidos: " + invalidFields);
        this.invalidFields = List.copyOf(invalidFields);
    }

    public List<String> getInvalidFields() {
        return invalidFields;
    }
}
