package ec.com.technoloqie.auditbot.api.dto;

import java.math.BigDecimal;

/**
 * Representa la clasificación generada por el modelo de IA para una consulta individual.
 *
 * @param messageId       identificador del mensaje del usuario
 * @param questionText    texto de la consulta evaluada
 * @param isGap           indica si la respuesta del asistente evidenció una brecha de conocimiento
 * @param confidenceScore grado de confianza de la clasificación (escala 0.000 a 1.000)
 * @param reason          justificación técnica o motivo proporcionado por el modelo
 */
public record KnowledgeGapCandidateDto(
        String messageId,
        String questionText,
        boolean isGap,
        BigDecimal confidenceScore,
        String reason
) {
}
