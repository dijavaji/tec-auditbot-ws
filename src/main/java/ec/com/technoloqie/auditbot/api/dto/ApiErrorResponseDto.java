package ec.com.technoloqie.auditbot.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * Estructura estandarizada de respuesta para errores de la API REST.
 *
 * @param timestamp     instante en que ocurrió el error
 * @param status        código de estado HTTP
 * @param error         descripción corta del estado HTTP
 * @param message       mensaje descriptivo del error
 * @param invalidFields lista opcional de rutas de campos que fallaron la validación
 */
public record ApiErrorResponseDto(
        Instant timestamp,
        int status,
        String error,
        String message,
        List<String> invalidFields) {

    public ApiErrorResponseDto(Instant timestamp, int status, String error, String message) {
        this(timestamp, status, error, message, List.of());
    }

    public ApiErrorResponseDto {
        invalidFields = invalidFields == null ? List.of() : List.copyOf(invalidFields);
    }
}
