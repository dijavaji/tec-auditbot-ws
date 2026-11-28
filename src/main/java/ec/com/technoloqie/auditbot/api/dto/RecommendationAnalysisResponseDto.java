package ec.com.technoloqie.auditbot.api.dto;

import java.util.List;

/**
 * Contenedor de respuesta estructurada retornado por el modelo de IA.
 */
public record RecommendationAnalysisResponseDto(
        List<RecommendationCandidateDto> recommendations
) {}
