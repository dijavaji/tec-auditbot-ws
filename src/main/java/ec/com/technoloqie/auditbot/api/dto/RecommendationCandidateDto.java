package ec.com.technoloqie.auditbot.api.dto;

/**
 * Candidato individual de recomendación retornado por el modelo de IA.
 */
public record RecommendationCandidateDto(
        String type,
        String suggestedText,
        String originMessageId
) {}
