package ec.com.technoloqie.auditbot.api.dto;

import ec.com.technoloqie.auditbot.api.model.SentimentLabel;
import java.math.BigDecimal;

/**
 * Representa la clasificación de sentimiento individual asignada por el modelo de IA
 * a un mensaje de usuario (US-04).
 *
 * @param messageId identificador del mensaje evaluado
 * @param label     etiqueta categórica de sentimiento asignada
 * @param score     score de certeza o confianza en escala 0.000 a 1.000
 */
public record SentimentItemClassificationDto(
        String messageId,
        SentimentLabel label,
        BigDecimal score) {}
