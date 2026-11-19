package ec.com.technoloqie.auditbot.api.dto;

import ec.com.technoloqie.auditbot.api.model.SentimentLabel;
import java.math.BigDecimal;

/**
 * Representa un sentimiento validado y procesado listo para persistencia (US-04).
 *
 * @param messageId     identificador del mensaje evaluado
 * @param label         etiqueta de sentimiento
 * @param score         score validado en rango [0.000, 1.000]
 * @param sequenceOrder posición secuencial ascendente (1, 2, ...) del mensaje en la conversación
 */
public record ProcessedSentimentDto(
        String messageId,
        SentimentLabel label,
        BigDecimal score,
        int sequenceOrder) {}
