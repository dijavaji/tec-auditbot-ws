package ec.com.technoloqie.auditbot.api.commons.exception;

/**
 * Error transitorio en el procesamiento de un mensaje de auditoría.
 * <p>
 * Indica un fallo de infraestructura (base de datos no disponible, timeout,
 * deadlock) que puede desaparecer al repetir el mismo mensaje. El listener lo
 * propaga sin envolver para activar el retry acotado del contenedor.
 * <p>
 * No almacena el cuerpo JSON completo ni {@code messages[].content}.
 */
public final class AuditProcessingException extends RuntimeException {

    private final String chatId;

    public AuditProcessingException(String chatId, String reason, Throwable cause) {
        super(reason, cause);
        this.chatId = chatId;
    }

    public String getChatId() {
        return chatId;
    }
}
