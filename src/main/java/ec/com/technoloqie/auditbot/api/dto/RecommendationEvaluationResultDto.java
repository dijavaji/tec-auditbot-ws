package ec.com.technoloqie.auditbot.api.dto;

import java.util.List;

/**
 * Resultado integral evaluado por el analizador puro {@link ec.com.technoloqie.auditbot.api.analyzer.RecommendationAnalyzer}.
 */
public record RecommendationEvaluationResultDto(
        List<RecommendationItemDto> items
) {}
