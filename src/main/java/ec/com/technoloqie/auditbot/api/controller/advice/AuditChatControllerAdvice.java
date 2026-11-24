package ec.com.technoloqie.auditbot.api.controller.advice;

import java.time.Clock;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import ec.com.technoloqie.auditbot.api.commons.exception.AuditPublicationException;
import ec.com.technoloqie.auditbot.api.commons.exception.InvalidAuditRequestException;
import ec.com.technoloqie.auditbot.api.dto.ApiErrorResponseDto;
import lombok.extern.slf4j.Slf4j;

/**
 * Manejador global de excepciones para los controladores REST de auditoría.
 */
@RestControllerAdvice
@Slf4j
public class AuditChatControllerAdvice {

    private final Clock clock;

    public AuditChatControllerAdvice(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(InvalidAuditRequestException.class)
    public ResponseEntity<ApiErrorResponseDto> handleInvalidAuditRequest(InvalidAuditRequestException ex) {
        log.warn("Solicitud de auditoría inválida. camposErroneos={}", ex.getInvalidFields());
        ApiErrorResponseDto errorDto = new ApiErrorResponseDto(
                clock.instant(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "La solicitud de conversación no cumple el contrato requerido",
                ex.getInvalidFields()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDto);
    }

    @ExceptionHandler(AuditPublicationException.class)
    public ResponseEntity<ApiErrorResponseDto> handleAuditPublicationException(AuditPublicationException ex) {
        log.error("Fallo de publicación hacia RabbitMQ. eventId={}", ex.getEventId(), ex);
        ApiErrorResponseDto errorDto = new ApiErrorResponseDto(
                clock.instant(),
                HttpStatus.BAD_GATEWAY.value(),
                HttpStatus.BAD_GATEWAY.getReasonPhrase(),
                "Fallo en la publicación asíncrona hacia el broker de mensajería",
                List.of()
        );
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorDto);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponseDto> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Cuerpo JSON malformado o ilegible: {}", ex.getMessage());
        ApiErrorResponseDto errorDto = new ApiErrorResponseDto(
                clock.instant(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "El cuerpo de la petición JSON es inválido o está malformado",
                List.of()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDto);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponseDto> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .toList();
        log.warn("Fallo de validación de argumentos: {}", fieldErrors);
        ApiErrorResponseDto errorDto = new ApiErrorResponseDto(
                clock.instant(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Error en la validación de parámetros de la solicitud",
                fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDto);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponseDto> handleGenericException(Exception ex) {
        log.error("Error inesperado en endpoint REST de auditoría", ex);
        ApiErrorResponseDto errorDto = new ApiErrorResponseDto(
                clock.instant(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "Ocurrió un error interno al procesar la solicitud",
                List.of()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorDto);
    }
}
