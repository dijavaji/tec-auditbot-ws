package ec.com.technoloqie.auditbot.api.dto;

import java.math.BigDecimal;

/**
 * Representa un gap de conocimiento consolidado tras el proceso de deduplicación y agrupación por similitud.
 *
 * @param messageId        identificador del mensaje cronológico inicial donde se evidenció el gap
 * @param questionText     pregunta canónica representativa del grupo
 * @param confidenceScore  score de confianza consolidado (máximo entre las ocurrencias)
 * @param occurrencesCount cantidad de veces que se reiteró la consulta en la conversación
 */
public record KnowledgeGapItemDto(
        String messageId,
        String questionText,
        BigDecimal confidenceScore,
        int occurrencesCount
) {
}
