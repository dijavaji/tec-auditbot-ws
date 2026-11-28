package ec.com.technoloqie.auditbot.api.dto;

import ec.com.technoloqie.auditbot.api.model.RecommendationType;

/**
 * Recomendación validada y saneada por el analizador puro.
 */
public record RecommendationItemDto(
        RecommendationType type,
        String suggestedText,
        String originMessageId
) {}
